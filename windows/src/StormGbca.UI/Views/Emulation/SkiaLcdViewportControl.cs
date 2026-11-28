// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Platform;
using Avalonia.Rendering.SceneGraph;
using Avalonia.Skia;
using Avalonia.Threading;
using SkiaSharp;
using StormGbca.Core.Engine;
using StormGbca.Core.Native;

namespace StormGbca.UI.Views.Emulation;

/// <summary>
/// Hardware-accelerated Avalonia 12 + SkiaSharp 3.119.4 LCD viewport control supporting
/// real GPU SkSL shaders: Pass 0 Optical-Flow Frame Generation (60 -> 120 FPS),
/// AMD FSR 1 (12-tap EASU + 5-tap RCAS), AMD FSR 3 (32-phase Halton Temporal + YCoCg Variance Clip + RCAS),
/// and classic pixel/LCD/CRT shaders with automatic fallback to crisp SKSamplingOptions.
/// </summary>
public sealed class SkiaLcdViewportControl : Control
{
    public static string ActiveRendererInfo { get; private set; } =
        "Auto (Vulkan -> OpenGL/ANGLE -> Software, min. OpenGL 3.0 / Vulkan 1.1)";

    public static event Action<string>? RendererInfoDetected;

    private static readonly SKSamplingOptions s_nearestSampling = new(SKFilterMode.Nearest);
    private static readonly SKSamplingOptions s_linearSampling = new(SKFilterMode.Linear);

    private static readonly object s_shaderLock = new();
    private static bool s_shadersInitialized;
    private static SKRuntimeEffect? s_frameGenEffect;
    private static SKRuntimeEffect? s_fsr1EasuEffect;
    private static SKRuntimeEffect? s_fsr3TemporalEffect;
    private static SKRuntimeEffect? s_rcasEffect;
    private static SKRuntimeEffect? s_sharpBilinearEffect;
    private static SKRuntimeEffect? s_lcdGridEffect;
    private static SKRuntimeEffect? s_rgbSubpixelEffect;
    private static SKRuntimeEffect? s_crtScanlinesEffect;
    private static SKRuntimeEffect? s_xbrzLiteEffect;

    private byte[] _prevBgra = [];
    private byte[] _currBgra = [];
    private int _srcWidth;
    private int _srcHeight;
    private long _frameSeq;
    private long _lastCountedSeq;
    private uint _haltonIndex;
    private float _fgProgress;
    private StormVideoFilter _filter = StormVideoFilter.LcdGrid;
    private int _fsrSharpness = 65;
    private int _fsrQualityMode;
    private bool _frameGeneration;

    private nint _lastGrHandle;
    private SKImage? _fsr3History;
    private int _fsr3HistW;
    private int _fsr3HistH;

    public int SourceWidth => _srcWidth;
    public int SourceHeight => _srcHeight;

    public SkiaLcdViewportControl()
    {
        ClipToBounds = true;
    }

    public static string DescribeRenderer(GRContext? grContext)
    {
        if (grContext == null)
        {
            return "Software Skia CPU (min. OpenGL 3.0 / Vulkan 1.1 for GPU)";
        }

        return grContext.Backend switch
        {
            GRBackend.Vulkan => "Vulkan / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
            GRBackend.OpenGL => "OpenGL / ANGLE / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
            GRBackend.Direct3D => "Direct3D / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
            GRBackend.Dawn => "Dawn WebGPU / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
            GRBackend.Metal => "Metal / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
            _ => $"{grContext.Backend} / Skia GPU (min. OpenGL 3.0 / Vulkan 1.1)",
        };
    }

    public static void UpdateRendererInfo(GRContext? grContext)
    {
        string info = DescribeRenderer(grContext);
        if (!string.Equals(ActiveRendererInfo, info, StringComparison.Ordinal))
        {
            ActiveRendererInfo = info;
            Dispatcher.UIThread.Post(() => RendererInfoDetected?.Invoke(info));
        }
    }

    public void UpdateFromSession(
        GbcaEmulatorSession emu,
        StormVideoFilter filter,
        int fsrSharpness,
        int fsrQualityMode,
        bool frameGeneration)
    {
        if (!emu.CopyFramePairBgra(ref _prevBgra, ref _currBgra, out int w, out int h, out long seq, out float progress))
        {
            return;
        }

        if (seq != _lastCountedSeq)
        {
            _lastCountedSeq = seq;
            _haltonIndex = (_haltonIndex + 1) & 31u;
        }

        _srcWidth = w;
        _srcHeight = h;
        _frameSeq = seq;
        _fgProgress = progress;
        _filter = filter;
        _fsrSharpness = Math.Clamp(fsrSharpness, 0, 100);
        _fsrQualityMode = Math.Clamp(fsrQualityMode, 0, 3);
        _frameGeneration = frameGeneration;

        InvalidateVisual();
    }

    public void ResetHistory()
    {
        _fsr3History?.Dispose();
        _fsr3History = null;
        _fsr3HistW = 0;
        _fsr3HistH = 0;
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        ResetHistory();
        base.OnDetachedFromVisualTree(e);
    }

    public override void Render(DrawingContext context)
    {
        var bounds = new Rect(0, 0, Bounds.Width, Bounds.Height);
        if (bounds.Width < 1 || bounds.Height < 1) return;
        context.Custom(new LcdDrawOperation(this, bounds));
    }

    private void RenderSkiaFrame(SKCanvas canvas, GRContext? gr, Rect bounds)
    {
        int srcW = _srcWidth;
        int srcH = _srcHeight;
        int need = srcW * srcH * 4;
        if (srcW <= 0 || srcH <= 0 || _currBgra.Length < need)
        {
            canvas.Clear(SKColors.Black);
            return;
        }

        nint grHandle = gr?.Handle ?? nint.Zero;
        if (grHandle != _lastGrHandle)
        {
            ResetHistory();
            _lastGrHandle = grHandle;
        }

        EnsureShadersCompiled();

        var srcInfo = new SKImageInfo(srcW, srcH, SKColorType.Bgra8888, SKAlphaType.Premul);
        using var currImage = SKImage.FromPixelCopy(srcInfo, _currBgra.AsSpan(0, need), srcW * 4);
        if (currImage == null)
        {
            canvas.Clear(SKColors.Black);
            return;
        }

        bool needPrev = (_frameGeneration && _fgProgress > 0.05f && _fgProgress < 0.95f && _frameSeq >= 2 && _prevBgra.Length >= need)
                        || (_filter == StormVideoFilter.Fsr3 && _prevBgra.Length >= need);
        using var prevImage = needPrev
            ? SKImage.FromPixelCopy(srcInfo, _prevBgra.AsSpan(0, need), srcW * 4)
            : null;

        SKImage? fgImage = null;
        SKImage workingImage = currImage;

        try
        {
            // Pass 0: Optional Optical-Flow Midpoint Frame Generation (60 -> 120 FPS)
            if (_frameGeneration && _fgProgress > 0.05f && _fgProgress < 0.95f && _frameSeq >= 2 &&
                prevImage != null && s_frameGenEffect != null)
            {
                using var fgSurface = CreateOffscreenSurface(gr, srcW, srcH);
                if (fgSurface != null)
                {
                    using var prevShader = prevImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_linearSampling);
                    using var currShader = currImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_linearSampling);
                    using var uniforms = new SKRuntimeEffectUniforms(s_frameGenEffect)
                    {
                        ["uSrcSize"] = new SKPoint(srcW, srcH),
                        ["uProgress"] = _fgProgress,
                    };
                    using var children = new SKRuntimeEffectChildren(s_frameGenEffect)
                    {
                        ["uPrev"] = prevShader,
                        ["uCurr"] = currShader,
                    };
                    using var effectShader = s_frameGenEffect.ToShader(uniforms, children);
                    if (effectShader != null)
                    {
                        using var paint = new SKPaint { Shader = effectShader };
                        fgSurface.Canvas.Clear(SKColors.Black);
                        fgSurface.Canvas.DrawRect(SKRect.Create(0, 0, srcW, srcH), paint);
                        fgImage = fgSurface.Snapshot();
                        if (fgImage != null)
                        {
                            workingImage = fgImage;
                        }
                    }
                }
            }

            var dstRect = SKRect.Create(0, 0, (float)bounds.Width, (float)bounds.Height);
            var totalMat = canvas.TotalMatrix;
            float scaleX = MathF.Sqrt(totalMat.ScaleX * totalMat.ScaleX + totalMat.SkewY * totalMat.SkewY);
            float scaleY = MathF.Sqrt(totalMat.SkewX * totalMat.SkewX + totalMat.ScaleY * totalMat.ScaleY);
            int outW = Math.Clamp((int)Math.Round(bounds.Width * Math.Max(1f, scaleX)), srcW, 3840);
            int outH = Math.Clamp((int)Math.Round(bounds.Height * Math.Max(1f, scaleY)), srcH, 2160);

            if (_filter == StormVideoFilter.Fsr1)
            {
                if (RenderFsr1(canvas, gr, workingImage, srcW, srcH, outW, outH, dstRect))
                {
                    return;
                }
            }
            else if (_filter == StormVideoFilter.Fsr3)
            {
                if (RenderFsr3(canvas, gr, workingImage, prevImage ?? workingImage, srcW, srcH, outW, outH, dstRect))
                {
                    return;
                }
            }
            else
            {
                ResetHistory();
                SKRuntimeEffect? effect = _filter switch
                {
                    StormVideoFilter.SharpBilinear => s_sharpBilinearEffect,
                    StormVideoFilter.LcdGrid => s_lcdGridEffect,
                    StormVideoFilter.RgbSubpixel => s_rgbSubpixelEffect,
                    StormVideoFilter.CrtScanlines => s_crtScanlinesEffect,
                    StormVideoFilter.XbrzLite => s_xbrzLiteEffect,
                    _ => null,
                };

                if (effect != null)
                {
                    var childSampling = _filter == StormVideoFilter.SharpBilinear ? s_linearSampling : s_nearestSampling;
                    using var texShader = workingImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, childSampling);
                    using var uniforms = new SKRuntimeEffectUniforms(effect)
                    {
                        ["uSrcSize"] = new SKPoint(srcW, srcH),
                        ["uDstSize"] = new SKPoint(dstRect.Width, dstRect.Height),
                    };
                    using var children = new SKRuntimeEffectChildren(effect)
                    {
                        ["uTex"] = texShader,
                    };
                    using var shader = effect.ToShader(uniforms, children);
                    if (shader != null)
                    {
                        using var paint = new SKPaint { Shader = shader };
                        canvas.DrawRect(dstRect, paint);
                        return;
                    }
                }
            }

            var fallbackSampling = _filter is StormVideoFilter.Nearest or StormVideoFilter.LcdGrid or StormVideoFilter.RgbSubpixel
                ? s_nearestSampling
                : s_linearSampling;
            canvas.DrawImage(workingImage, dstRect, fallbackSampling, null);
        }
        finally
        {
            fgImage?.Dispose();
        }
    }

    private bool RenderFsr1(
        SKCanvas canvas,
        GRContext? gr,
        SKImage srcImage,
        int srcW,
        int srcH,
        int outW,
        int outH,
        SKRect dstRect)
    {
        ResetHistory();
        if (s_fsr1EasuEffect == null || s_rcasEffect == null) return false;

        ComputeQualityTargetSize(srcW, srcH, outW, outH, out int pass1W, out int pass1H);
        using var pass1Surface = CreateOffscreenSurface(gr, pass1W, pass1H);
        if (pass1Surface == null) return false;

        using var srcShader = srcImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_nearestSampling);
        using var easuUniforms = new SKRuntimeEffectUniforms(s_fsr1EasuEffect)
        {
            ["uSrcSize"] = new SKPoint(srcW, srcH),
            ["uDstSize"] = new SKPoint(pass1W, pass1H),
        };
        using var easuChildren = new SKRuntimeEffectChildren(s_fsr1EasuEffect)
        {
            ["uTex"] = srcShader,
        };
        using var easuShader = s_fsr1EasuEffect.ToShader(easuUniforms, easuChildren);
        if (easuShader == null) return false;

        using (var easuPaint = new SKPaint { Shader = easuShader })
        {
            pass1Surface.Canvas.Clear(SKColors.Black);
            pass1Surface.Canvas.DrawRect(SKRect.Create(0, 0, pass1W, pass1H), easuPaint);
        }

        using var pass1Image = pass1Surface.Snapshot();
        if (pass1Image == null) return false;

        return DrawRcasPass(canvas, pass1Image, pass1W, pass1H, dstRect);
    }

    private bool RenderFsr3(
        SKCanvas canvas,
        GRContext? gr,
        SKImage currImage,
        SKImage prevImage,
        int srcW,
        int srcH,
        int outW,
        int outH,
        SKRect dstRect)
    {
        if (s_fsr3TemporalEffect == null || s_rcasEffect == null) return false;

        ComputeQualityTargetSize(srcW, srcH, outW, outH, out int pass1W, out int pass1H);
        using var pass1Surface = CreateOffscreenSurface(gr, pass1W, pass1H);
        if (pass1Surface == null) return false;

        bool hasHistory = _fsr3History != null && _fsr3HistW == pass1W && _fsr3HistH == pass1H;
        SKImage historySource = hasHistory ? _fsr3History! : currImage;

        float jx = (Halton((int)_haltonIndex + 1, 2) - 0.5f) * 0.35f;
        float jy = (Halton((int)_haltonIndex + 1, 3) - 0.5f) * 0.35f;

        using var currShader = currImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_nearestSampling);
        using var prevShader = prevImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_linearSampling);
        using var histShader = historySource.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_linearSampling);

        using var tUniforms = new SKRuntimeEffectUniforms(s_fsr3TemporalEffect)
        {
            ["uSrcSize"] = new SKPoint(srcW, srcH),
            ["uDstSize"] = new SKPoint(pass1W, pass1H),
            ["uJitter"] = new SKPoint(jx, jy),
            ["uHasHistory"] = hasHistory ? 1.0f : 0.0f,
        };
        using var tChildren = new SKRuntimeEffectChildren(s_fsr3TemporalEffect)
        {
            ["uTex"] = currShader,
            ["uPrev"] = prevShader,
            ["uHistory"] = histShader,
        };
        using var tShader = s_fsr3TemporalEffect.ToShader(tUniforms, tChildren);
        if (tShader == null) return false;

        using (var tPaint = new SKPaint { Shader = tShader })
        {
            pass1Surface.Canvas.Clear(SKColors.Black);
            pass1Surface.Canvas.DrawRect(SKRect.Create(0, 0, pass1W, pass1H), tPaint);
        }

        var nextHistory = pass1Surface.Snapshot();
        if (nextHistory == null) return false;

        _fsr3History?.Dispose();
        _fsr3History = nextHistory;
        _fsr3HistW = pass1W;
        _fsr3HistH = pass1H;

        return DrawRcasPass(canvas, nextHistory, pass1W, pass1H, dstRect);
    }

    private bool DrawRcasPass(SKCanvas canvas, SKImage pass1Image, int pass1W, int pass1H, SKRect dstRect)
    {
        if (s_rcasEffect == null) return false;

        float sharpness = Math.Clamp(_fsrSharpness / 100f, 0f, 1f);
        float qualityBias = _fsrQualityMode switch
        {
            1 => 0.08f,
            2 => 0.16f,
            3 => 0.24f,
            _ => 0.0f,
        };

        using var pass1Shader = pass1Image.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, s_linearSampling);
        using var rUniforms = new SKRuntimeEffectUniforms(s_rcasEffect)
        {
            ["uSrcSize"] = new SKPoint(pass1W, pass1H),
            ["uDstSize"] = new SKPoint(dstRect.Width, dstRect.Height),
            ["uSharpness"] = sharpness,
            ["uQualityBias"] = qualityBias,
        };
        using var rChildren = new SKRuntimeEffectChildren(s_rcasEffect)
        {
            ["uTex"] = pass1Shader,
        };
        using var rShader = s_rcasEffect.ToShader(rUniforms, rChildren);
        if (rShader == null) return false;

        using var rPaint = new SKPaint { Shader = rShader };
        canvas.DrawRect(dstRect, rPaint);
        return true;
    }

    private void ComputeQualityTargetSize(int srcW, int srcH, int outW, int outH, out int pass1W, out int pass1H)
    {
        float scale = _fsrQualityMode switch
        {
            1 => 0.59f,
            2 => 0.50f,
            3 => 0.33f,
            _ => 0.67f,
        };
        // Keep full output resolution when viewport is near native size; scale down smoothly at 1080p/4K
        if (outW <= srcW * 2)
        {
            pass1W = outW;
            pass1H = outH;
            return;
        }
        pass1W = Math.Clamp((int)Math.Round(outW * scale), srcW, outW);
        pass1H = Math.Clamp((int)Math.Round(outH * scale), srcH, outH);
    }

    private static SKSurface? CreateOffscreenSurface(GRContext? gr, int width, int height)
    {
        var info = new SKImageInfo(width, height, SKColorType.Bgra8888, SKAlphaType.Premul);
        if (gr != null)
        {
            var gpuSurf = SKSurface.Create(gr, false, info);
            if (gpuSurf != null) return gpuSurf;
        }
        return SKSurface.Create(info);
    }

    private static float Halton(int index, int radix)
    {
        float f = 1f;
        float r = 0f;
        int i = index;
        while (i > 0)
        {
            f /= radix;
            r += f * (i % radix);
            i /= radix;
        }
        return r;
    }

    private static void EnsureShadersCompiled()
    {
        if (s_shadersInitialized) return;
        lock (s_shaderLock)
        {
            if (s_shadersInitialized) return;
            s_frameGenEffect = CompileEffect(FrameGenSksl, "FrameGen");
            s_fsr1EasuEffect = CompileEffect(Fsr1EasuSksl, "FSR1_EASU");
            s_fsr3TemporalEffect = CompileEffect(Fsr3TemporalSksl, "FSR3_Temporal");
            s_rcasEffect = CompileEffect(RcasSksl, "FSR_RCAS");
            s_sharpBilinearEffect = CompileEffect(SharpBilinearSksl, "SharpBilinear");
            s_lcdGridEffect = CompileEffect(LcdGridSksl, "LcdGrid");
            s_rgbSubpixelEffect = CompileEffect(RgbSubpixelSksl, "RgbSubpixel");
            s_crtScanlinesEffect = CompileEffect(CrtScanlinesSksl, "CrtScanlines");
            s_xbrzLiteEffect = CompileEffect(XbrzLiteSksl, "XbrzLite");
            s_shadersInitialized = true;
        }
    }

    private static SKRuntimeEffect? CompileEffect(string sksl, string tag)
    {
        try
        {
            var effect = SKRuntimeEffect.CreateShader(sksl, out string errorText);
            if (effect == null && !string.IsNullOrWhiteSpace(errorText))
            {
                Console.Error.WriteLine($"[SkiaLcdViewportControl] SkSL {tag} error: {errorText}");
            }
            return effect;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[SkiaLcdViewportControl] SkSL {tag} exception: {ex.Message}");
            return null;
        }
    }

    private sealed class LcdDrawOperation : ICustomDrawOperation
    {
        private readonly SkiaLcdViewportControl _owner;

        public LcdDrawOperation(SkiaLcdViewportControl owner, Rect bounds)
        {
            _owner = owner;
            Bounds = bounds;
        }

        public Rect Bounds { get; }

        public bool HitTest(Point p) => Bounds.Contains(p);

        public bool Equals(ICustomDrawOperation? other) => false;

        public void Dispose()
        {
        }

        public void Render(ImmediateDrawingContext context)
        {
            var leaseFeature = context.TryGetFeature<ISkiaSharpApiLeaseFeature>();
            if (leaseFeature == null)
            {
                context.FillRectangle(Brushes.Black, Bounds);
                return;
            }

            using var lease = leaseFeature.Lease();
            UpdateRendererInfo(lease.GrContext);
            _owner.RenderSkiaFrame(lease.SkCanvas, lease.GrContext, Bounds);
        }
    }

    // -------------------------------------------------------------------------
    // SkSL Shaders (SkiaSharp 3.119.4 - Pixel Coordinates for .eval())
    // -------------------------------------------------------------------------

    private const string FrameGenSksl = @"
uniform shader uPrev;
uniform shader uCurr;
uniform float2 uSrcSize;
uniform float uProgress;

float luma(float3 c) {
    return dot(c, float3(0.299, 0.587, 0.114));
}

float3 samplePrev(float2 p) {
    return float3(uPrev.eval(clamp(p, float2(0.5), uSrcSize - float2(0.5))).rgb);
}

float3 sampleCurr(float2 p) {
    return float3(uCurr.eval(clamp(p, float2(0.5), uSrcSize - float2(0.5))).rgb);
}

half4 main(float2 fragCoord) {
    float Ixx = 0.0;
    float Iyy = 0.0;
    float Ixy = 0.0;
    float Ixt = 0.0;
    float Iyt = 0.0;
    float diffSum = 0.0;

    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            float2 p = fragCoord + float2(float(dx), float(dy));
            float l0 = luma(samplePrev(p));
            float l1 = luma(sampleCurr(p));
            float lx0 = luma(samplePrev(p + float2(1.0, 0.0))) - luma(samplePrev(p - float2(1.0, 0.0)));
            float lx1 = luma(sampleCurr(p + float2(1.0, 0.0))) - luma(sampleCurr(p - float2(1.0, 0.0)));
            float ly0 = luma(samplePrev(p + float2(0.0, 1.0))) - luma(samplePrev(p - float2(0.0, 1.0)));
            float ly1 = luma(sampleCurr(p + float2(0.0, 1.0))) - luma(sampleCurr(p - float2(0.0, 1.0)));
            float Ix = 0.25 * (lx0 + lx1);
            float Iy = 0.25 * (ly0 + ly1);
            float It = l1 - l0;
            Ixx += Ix * Ix;
            Iyy += Iy * Iy;
            Ixy += Ix * Iy;
            Ixt += Ix * It;
            Iyt += Iy * It;
            diffSum += abs(It);
        }
    }

    float reg = 0.02;
    float a = Ixx + reg;
    float d = Iyy + reg;
    float det = max(a * d - Ixy * Ixy, 0.0001);
    float2 flow = float2(-(d * Ixt - Ixy * Iyt), -(a * Iyt - Ixy * Ixt)) / det;
    float flowLen = length(flow);
    if (flowLen > 6.0) {
        flow *= 6.0 / flowLen;
    }

    float3 wPrev = samplePrev(fragCoord - flow * uProgress);
    float3 wCurr = sampleCurr(fragCoord + flow * (1.0 - uProgress));
    float3 rPrev = samplePrev(fragCoord);
    float3 rCurr = sampleCurr(fragCoord);
    float3 rawBlend = mix(rPrev, rCurr, uProgress);
    float3 warpedBlend = mix(wPrev, wCurr, uProgress);

    float avgDiff = diffSum / 9.0;
    float warpErr = length(wPrev - wCurr);
    float disocc = smoothstep(0.10, 0.36, warpErr);
    float sceneCut = smoothstep(0.28, 0.45, avgDiff);
    float3 cutFrame = uProgress < 0.5 ? rPrev : rCurr;
    float3 res = mix(warpedBlend, rawBlend, disocc);
    res = mix(res, cutFrame, sceneCut);
    return half4(half3(clamp(res, 0.0, 1.0)), 1.0);
}
";

    private const string Fsr1EasuSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

float3 tap(float2 ip) {
    float2 p = clamp(ip + float2(0.5), float2(0.5), uSrcSize - float2(0.5));
    return float3(uTex.eval(p).rgb);
}

float luma(float3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

void easuSet(inout float2 dir, inout float len, float w, float lA, float lB, float lC, float lD, float lE) {
    float lenX = max(abs(lD - lC), abs(lC - lB));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) / max(lenX, 0.0001), 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;

    float lenY = max(abs(lE - lC), abs(lC - lA));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) / max(lenY, 0.0001), 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

float easuTapWeight(float2 off, float2 dir, float2 scale, float lob) {
    float2 v = float2(
        off.x * dir.x + off.y * dir.y,
        off.x * (-dir.y) + off.y * dir.x
    );
    v *= scale;
    float d2 = min(dot(v, v), lob);
    float wB = 0.4 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    return wB * wA;
}

half4 main(float2 fragCoord) {
    float2 pp = fragCoord * (uSrcSize / uDstSize) - float2(0.5);
    float2 fp = floor(pp);
    float2 f = pp - fp;

    // 12-tap EASU neighborhood
    float3 b = tap(fp + float2(0.0, -1.0));
    float3 c = tap(fp + float2(1.0, -1.0));
    float3 e = tap(fp + float2(-1.0, 0.0));
    float3 f0 = tap(fp + float2(0.0, 0.0));
    float3 g = tap(fp + float2(1.0, 0.0));
    float3 h = tap(fp + float2(2.0, 0.0));
    float3 i = tap(fp + float2(-1.0, 1.0));
    float3 j = tap(fp + float2(0.0, 1.0));
    float3 k = tap(fp + float2(1.0, 1.0));
    float3 l = tap(fp + float2(2.0, 1.0));
    float3 n = tap(fp + float2(0.0, 2.0));
    float3 o = tap(fp + float2(1.0, 2.0));

    float lb = luma(b); float lc = luma(c);
    float le = luma(e); float lf = luma(f0); float lg = luma(g); float lh = luma(h);
    float li = luma(i); float lj = luma(j); float lk = luma(k); float ll = luma(l);
    float ln = luma(n); float lo = luma(o);

    float2 dir = float2(0.0);
    float len = 0.0;
    easuSet(dir, len, (1.0 - f.x) * (1.0 - f.y), lb, le, lf, lg, lj);
    easuSet(dir, len, f.x * (1.0 - f.y), lc, lf, lg, lh, lk);
    easuSet(dir, len, (1.0 - f.x) * f.y, lf, li, lj, lk, ln);
    easuSet(dir, len, f.x * f.y, lg, lj, lk, ll, lo);

    float dirR = dir.x * dir.x + dir.y * dir.y;
    bool zeroDir = dirR < 0.00001;
    dir = zeroDir ? float2(1.0, 0.0) : dir * (1.0 / sqrt(dirR));

    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(abs(dir.x), abs(dir.y));
    float2 aniso = float2(mix(1.0, stretch, len), mix(1.0, 0.5, len));
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;

    float3 sum = float3(0.0);
    float wSum = 0.0;

    float w;
    w = easuTapWeight(float2(0.0, -1.0) - f, dir, aniso, lob); sum += b * w; wSum += w;
    w = easuTapWeight(float2(1.0, -1.0) - f, dir, aniso, lob); sum += c * w; wSum += w;
    w = easuTapWeight(float2(-1.0, 0.0) - f, dir, aniso, lob); sum += e * w; wSum += w;
    w = easuTapWeight(float2(0.0, 0.0) - f, dir, aniso, lob); sum += f0 * w; wSum += w;
    w = easuTapWeight(float2(1.0, 0.0) - f, dir, aniso, lob); sum += g * w; wSum += w;
    w = easuTapWeight(float2(2.0, 0.0) - f, dir, aniso, lob); sum += h * w; wSum += w;
    w = easuTapWeight(float2(-1.0, 1.0) - f, dir, aniso, lob); sum += i * w; wSum += w;
    w = easuTapWeight(float2(0.0, 1.0) - f, dir, aniso, lob); sum += j * w; wSum += w;
    w = easuTapWeight(float2(1.0, 1.0) - f, dir, aniso, lob); sum += k * w; wSum += w;
    w = easuTapWeight(float2(2.0, 1.0) - f, dir, aniso, lob); sum += l * w; wSum += w;
    w = easuTapWeight(float2(0.0, 2.0) - f, dir, aniso, lob); sum += n * w; wSum += w;
    w = easuTapWeight(float2(1.0, 2.0) - f, dir, aniso, lob); sum += o * w; wSum += w;

    float3 min4 = min(min(f0, g), min(j, k));
    float3 max4 = max(max(f0, g), max(j, k));
    float3 res = clamp(sum / max(wSum, 0.0001), min4, max4);
    return half4(half3(res), 1.0);
}
";

    private const string Fsr3TemporalSksl = @"
uniform shader uTex;
uniform shader uPrev;
uniform shader uHistory;
uniform float2 uSrcSize;
uniform float2 uDstSize;
uniform float2 uJitter;
uniform float uHasHistory;

float luma(float3 c) {
    return dot(c, float3(0.299, 0.587, 0.114));
}

float3 rgbToYCoCg(float3 c) {
    return float3(
        0.25 * c.r + 0.50 * c.g + 0.25 * c.b,
        0.50 * c.r - 0.50 * c.b,
        -0.25 * c.r + 0.50 * c.g - 0.25 * c.b
    );
}

float3 yCoCgToRgb(float3 ycc) {
    float t = ycc.x - ycc.z;
    return clamp(float3(t + ycc.y, ycc.x + ycc.z, t - ycc.y), 0.0, 1.0);
}

float3 tapCurr(float2 ip) {
    float2 p = clamp(ip + float2(0.5), float2(0.5), uSrcSize - float2(0.5));
    return float3(uTex.eval(p).rgb);
}

float3 samplePrevLin(float2 p) {
    return float3(uPrev.eval(clamp(p, float2(0.5), uSrcSize - float2(0.5))).rgb);
}

float catmullWeight(float x) {
    float ax = abs(x);
    if (ax < 1.0) {
        return 1.5 * ax * ax * ax - 2.5 * ax * ax + 1.0;
    }
    if (ax < 2.0) {
        return -0.5 * ax * ax * ax + 2.5 * ax * ax - 4.0 * ax + 2.0;
    }
    return 0.0;
}

float3 sampleCatmullRom(float2 pp) {
    float2 fp = floor(pp);
    float2 f = pp - fp;
    float3 sum = float3(0.0);
    float wSum = 0.0;
    for (int dy = -1; dy <= 2; dy++) {
        float wy = catmullWeight(float(dy) - f.y);
        for (int dx = -1; dx <= 2; dx++) {
            float wx = catmullWeight(float(dx) - f.x);
            float w = wx * wy;
            sum += tapCurr(fp + float2(float(dx), float(dy))) * w;
            wSum += w;
        }
    }
    float3 c00 = tapCurr(fp);
    float3 c10 = tapCurr(fp + float2(1.0, 0.0));
    float3 c01 = tapCurr(fp + float2(0.0, 1.0));
    float3 c11 = tapCurr(fp + float2(1.0, 1.0));
    float3 mn = min(min(c00, c10), min(c01, c11));
    float3 mx = max(max(c00, c10), max(c01, c11));
    return clamp(sum / max(wSum, 0.0001), mn, mx);
}

half4 main(float2 fragCoord) {
    float2 srcCoord = fragCoord * (uSrcSize / uDstSize);
    float2 pp = srcCoord - float2(0.5) + uJitter;
    float3 currRgb = sampleCatmullRom(pp);

    if (uHasHistory < 0.5) {
        return half4(half3(currRgb), 1.0);
    }

    // 3x3 YCoCg variance clipping (gamma = 1.25) + 3x3 optical flow reprojection
    float3 m1 = float3(0.0);
    float3 m2 = float3(0.0);
    float Ixx = 0.0;
    float Iyy = 0.0;
    float Ixy = 0.0;
    float Ixt = 0.0;
    float Iyt = 0.0;
    float diffSum = 0.0;
    float2 centerIp = floor(srcCoord);

    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            float2 ip = centerIp + float2(float(dx), float(dy));
            float3 c = tapCurr(ip);
            float3 ycc = rgbToYCoCg(c);
            m1 += ycc;
            m2 += ycc * ycc;

            float2 p = ip + float2(0.5);
            float l0 = luma(samplePrevLin(p));
            float l1 = luma(c);
            float lx0 = luma(samplePrevLin(p + float2(1.0, 0.0))) - luma(samplePrevLin(p - float2(1.0, 0.0)));
            float lx1 = luma(tapCurr(ip + float2(1.0, 0.0))) - luma(tapCurr(ip - float2(1.0, 0.0)));
            float ly0 = luma(samplePrevLin(p + float2(0.0, 1.0))) - luma(samplePrevLin(p - float2(0.0, 1.0)));
            float ly1 = luma(tapCurr(ip + float2(0.0, 1.0))) - luma(tapCurr(ip - float2(0.0, 1.0)));
            float Ix = 0.25 * (lx0 + lx1);
            float Iy = 0.25 * (ly0 + ly1);
            float It = l1 - l0;
            Ixx += Ix * Ix;
            Iyy += Iy * Iy;
            Ixy += Ix * Iy;
            Ixt += Ix * It;
            Iyt += Iy * It;
            diffSum += abs(It);
        }
    }

    m1 /= 9.0;
    m2 /= 9.0;
    float3 sigma = sqrt(max(m2 - m1 * m1, float3(0.0)));
    float3 boxMin = m1 - 1.25 * sigma;
    float3 boxMax = m1 + 1.25 * sigma;

    float reg = 0.025;
    float a = Ixx + reg;
    float d = Iyy + reg;
    float det = max(a * d - Ixy * Ixy, 0.0001);
    float2 flowSrc = float2(-(d * Ixt - Ixy * Iyt), -(a * Iyt - Ixy * Ixt)) / det;
    float flowLen = length(flowSrc);
    if (flowLen > 4.0) {
        flowSrc *= 4.0 / flowLen;
    }

    float2 flowDst = flowSrc * (uDstSize / uSrcSize);
    float2 histCoord = clamp(fragCoord - flowDst, float2(0.5), uDstSize - float2(0.5));
    float3 histRgb = float3(uHistory.eval(histCoord).rgb);
    float3 histYcc = clamp(rgbToYCoCg(histRgb), boxMin, boxMax);
    float3 clippedHist = yCoCgToRgb(histYcc);

    float avgDiff = diffSum / 9.0;
    float disocc = smoothstep(0.06, 0.28, avgDiff);
    float histWeight = mix(0.84, 0.14, disocc) * uHasHistory;
    float3 res = mix(currRgb, clippedHist, histWeight);
    return half4(half3(clamp(res, 0.0, 1.0)), 1.0);
}
";

    private const string RcasSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;
uniform float uSharpness;
uniform float uQualityBias;

float luma(float3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

float3 sampleSrc(float2 p) {
    return float3(uTex.eval(clamp(p, float2(0.5), uSrcSize - float2(0.5))).rgb);
}

half4 main(float2 fragCoord) {
    float2 pos = fragCoord * (uSrcSize / uDstSize);
    float3 c = sampleSrc(pos);
    float3 n = sampleSrc(pos + float2(0.0, -1.0));
    float3 s = sampleSrc(pos + float2(0.0, 1.0));
    float3 w = sampleSrc(pos + float2(-1.0, 0.0));
    float3 e = sampleSrc(pos + float2(1.0, 0.0));

    float lC = luma(c);
    float lN = luma(n);
    float lS = luma(s);
    float lW = luma(w);
    float lE = luma(e);

    float mn = min(lC, min(min(lN, lS), min(lW, lE)));
    float mx = max(lC, max(max(lN, lS), max(lW, lE)));

    float hitMin = mn / max(4.0 * mx, 0.0001);
    float hitMax = (1.0 - mx) / max(4.0 * (1.0 - mn), 0.0001);
    float amp = min(hitMin, hitMax);

    float peak = clamp(uSharpness + uQualityBias, 0.0, 1.0);
    float lobeMax = mix(0.0, 0.1875, peak);
    float nz = 0.25 * (lN + lS + lW + lE) - lC;
    float noiseSuppress = clamp(1.0 - abs(nz) / max(mx - mn, 0.0001), 0.0, 1.0);
    float weight = -clamp(amp, 0.0, lobeMax) * peak * (0.5 + 0.5 * noiseSuppress);

    float3 res = (c + weight * (n + s + w + e)) / max(1.0 + 4.0 * weight, 0.0001);
    return half4(half3(clamp(res, 0.0, 1.0)), 1.0);
}
";

    private const string SharpBilinearSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

half4 main(float2 fragCoord) {
    float2 scale = uDstSize / uSrcSize;
    float2 p = fragCoord / scale;
    float2 texel = floor(p);
    float2 f = p - texel;
    float2 region = 0.5 / max(scale, float2(1.0));
    float2 fSharp = clamp((f - 0.5) / max(region * 2.0, float2(0.001)) + 0.5, 0.0, 1.0);
    float2 sampleCoord = clamp(texel + fSharp, float2(0.5), uSrcSize - float2(0.5));
    return uTex.eval(sampleCoord);
}
";

    private const string LcdGridSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

half4 main(float2 fragCoord) {
    float2 p = fragCoord * (uSrcSize / uDstSize);
    float2 f = fract(p);
    float3 col = float3(uTex.eval(clamp(floor(p) + float2(0.5), float2(0.5), uSrcSize - float2(0.5))).rgb);
    float gx = smoothstep(0.0, 0.12, f.x) * (1.0 - smoothstep(0.88, 1.0, f.x));
    float gy = smoothstep(0.0, 0.12, f.y) * (1.0 - smoothstep(0.88, 1.0, f.y));
    float grid = mix(0.76, 1.0, gx * gy);
    return half4(half3(col * grid), 1.0);
}
";

    private const string RgbSubpixelSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

half4 main(float2 fragCoord) {
    float2 p = fragCoord * (uSrcSize / uDstSize);
    float2 f = fract(p);
    float3 col = float3(uTex.eval(clamp(floor(p) + float2(0.5), float2(0.5), uSrcSize - float2(0.5))).rgb);
    float3 sub = float3(
        smoothstep(0.0, 0.16, f.x) * (1.0 - smoothstep(0.32, 0.45, f.x)),
        smoothstep(0.28, 0.40, f.x) * (1.0 - smoothstep(0.62, 0.74, f.x)),
        smoothstep(0.58, 0.70, f.x) * (1.0 - smoothstep(0.88, 1.0, f.x))
    );
    float rowMask = mix(0.80, 1.0, smoothstep(0.0, 0.14, f.y) * (1.0 - smoothstep(0.86, 1.0, f.y)));
    float3 mask = mix(float3(0.78), float3(1.08), sub) * rowMask;
    return half4(half3(clamp(col * mask, 0.0, 1.0)), 1.0);
}
";

    private const string CrtScanlinesSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

half4 main(float2 fragCoord) {
    float2 p = fragCoord * (uSrcSize / uDstSize);
    float3 c0 = float3(uTex.eval(clamp(p, float2(0.5), uSrcSize - float2(0.5))).rgb);
    float3 cL = float3(uTex.eval(clamp(p - float2(0.5, 0.0), float2(0.5), uSrcSize - float2(0.5))).rgb);
    float3 cR = float3(uTex.eval(clamp(p + float2(0.5, 0.0), float2(0.5), uSrcSize - float2(0.5))).rgb);
    float3 col = 0.6 * c0 + 0.2 * (cL + cR);
    float scan = 0.84 + 0.16 * sin(p.y * 6.2831853);
    float grille = 0.94 + 0.06 * sin(fragCoord.x * 2.0943951);
    return half4(half3(clamp(col * scan * grille, 0.0, 1.0)), 1.0);
}
";

    private const string XbrzLiteSksl = @"
uniform shader uTex;
uniform float2 uSrcSize;
uniform float2 uDstSize;

float3 tap(float2 ip) {
    return float3(uTex.eval(clamp(ip + float2(0.5), float2(0.5), uSrcSize - float2(0.5))).rgb);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord * (uSrcSize / uDstSize);
    float2 ip = floor(p);
    float2 f = fract(p) - float2(0.5);
    float3 c = tap(ip);
    float3 n = tap(ip + float2(0.0, -1.0));
    float3 s = tap(ip + float2(0.0, 1.0));
    float3 w = tap(ip + float2(-1.0, 0.0));
    float3 e = tap(ip + float2(1.0, 0.0));

    float3 hCol = f.x < 0.0 ? w : e;
    float3 vCol = f.y < 0.0 ? n : s;
    float dHV = length(hCol - vCol);
    float dCH = length(c - hCol);
    float dCV = length(c - vCol);

    float diag = (dHV < 0.14 && dCH > 0.08 && dCV > 0.08)
        ? clamp((abs(f.x) + abs(f.y) - 0.5) * 1.6, 0.0, 0.55)
        : 0.0;
    float3 res = mix(c, 0.5 * (hCol + vCol), diag);
    return half4(half3(clamp(res, 0.0, 1.0)), 1.0);
}
";
}

/// <summary>
/// Lightweight 1x1 control placed in Settings to probe the real active Skia GPU backend immediately.
/// </summary>
public sealed class SkiaRendererProbeControl : Control
{
    public SkiaRendererProbeControl()
    {
        Width = 1;
        Height = 1;
        IsHitTestVisible = false;
        Opacity = 0.01;
    }

    public override void Render(DrawingContext context)
    {
        context.Custom(new ProbeOp(new Rect(0, 0, Math.Max(1, Bounds.Width), Math.Max(1, Bounds.Height))));
    }

    private sealed class ProbeOp(Rect bounds) : ICustomDrawOperation
    {
        public Rect Bounds { get; } = bounds;
        public bool HitTest(Point p) => false;
        public bool Equals(ICustomDrawOperation? other) => false;
        public void Dispose() { }

        public void Render(ImmediateDrawingContext context)
        {
            var leaseFeature = context.TryGetFeature<ISkiaSharpApiLeaseFeature>();
            if (leaseFeature == null) return;
            using var lease = leaseFeature.Lease();
            SkiaLcdViewportControl.UpdateRendererInfo(lease.GrContext);
        }
    }
}

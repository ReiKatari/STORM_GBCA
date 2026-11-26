// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using SkiaSharp;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;

namespace StormGbca.UI.Views.Emulation;

public sealed record ConsoleSkinPalette(
    string DisplayName,
    SKColor BodyTop,
    SKColor BodyBottom,
    SKColor BezelColor,
    SKColor AccentColor,
    SKColor TextColor,
    bool IsTranslucent,
    bool IsSpClamshell,
    bool IsMicro,
    bool IsNeon
);

/// <summary>
/// SkiaSharp procedural 3D console chassis renderer for GB (None + 8), GBC (None + 8), and GBA (None + 10).
/// Renders beveled 3D shells, metallic/translucent shaders, speaker slits, power LED, recessed lens bezel,
/// night dimming, and computes exact inner LCD viewport bounds.
/// </summary>
public static class GbcaConsoleSkinRenderer
{
    public static ConsoleSkinPalette GetGbPalette(GbConsoleSkin skin) => skin switch
    {
        GbConsoleSkin.DmgClassicGray => new("DMG Classic Gray", SKColor.Parse("#CFCBB8"), SKColor.Parse("#A8A391"), SKColor.Parse("#545764"), SKColor.Parse("#8B1D4E"), SKColor.Parse("#1F2A6B"), false, false, false, false),
        GbConsoleSkin.DmgPlayItLoudRed => new("Play It Loud Red", SKColor.Parse("#D92626"), SKColor.Parse("#991414"), SKColor.Parse("#2B2D36"), SKColor.Parse("#F59E0B"), SKColor.Parse("#F8FAFC"), false, false, false, false),
        GbConsoleSkin.DmgPlayItLoudBlue => new("Play It Loud Blue", SKColor.Parse("#2563EB"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#2B2D36"), SKColor.Parse("#38BDF8"), SKColor.Parse("#F8FAFC"), false, false, false, false),
        GbConsoleSkin.DmgPlayItLoudYellow => new("Play It Loud Yellow", SKColor.Parse("#FACC15"), SKColor.Parse("#CA8A04"), SKColor.Parse("#2B2D36"), SKColor.Parse("#EF4444"), SKColor.Parse("#1E293B"), false, false, false, false),
        GbConsoleSkin.DmgPlayItLoudBlack => new("Play It Loud Black", SKColor.Parse("#2A2D34"), SKColor.Parse("#14161A"), SKColor.Parse("#121318"), SKColor.Parse("#8BAC0F"), SKColor.Parse("#CBD5E1"), false, false, false, false),
        GbConsoleSkin.PocketSilver => new("Pocket Silver", SKColor.Parse("#E2E8F0"), SKColor.Parse("#94A3B8"), SKColor.Parse("#334155"), SKColor.Parse("#10B981"), SKColor.Parse("#1E293B"), false, false, false, false),
        GbConsoleSkin.GameBoyLightTeal => new("Game Boy Light Teal", SKColor.Parse("#22D3EE"), SKColor.Parse("#0891B2"), SKColor.Parse("#1E293B"), SKColor.Parse("#A5F3FC"), SKColor.Parse("#F8FAFC"), false, false, false, false),
        GbConsoleSkin.StormNeonGb => new("STORM Neon GB", SKColor.Parse("#17152B"), SKColor.Parse("#0B0A17"), SKColor.Parse("#080912"), SKColor.Parse("#8BAC0F"), SKColor.Parse("#A3E635"), false, false, false, true),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#8BAC0F"), SKColors.White, false, false, false, false),
    };

    public static ConsoleSkinPalette GetGbcPalette(GbcConsoleSkin skin) => skin switch
    {
        GbcConsoleSkin.AtomicPurple => new("Atomic Purple", SKColor.Parse("#7C3AED"), SKColor.Parse("#4C1D95"), SKColor.Parse("#1F212B"), SKColor.Parse("#A78BFA"), SKColor.Parse("#F8FAFC"), true, false, false, false),
        GbcConsoleSkin.Berry => new("Berry", SKColor.Parse("#E11D48"), SKColor.Parse("#9F1239"), SKColor.Parse("#1F212B"), SKColor.Parse("#FDA4AF"), SKColor.Parse("#FFF1F2"), false, false, false, false),
        GbcConsoleSkin.Dandelion => new("Dandelion", SKColor.Parse("#FBBF24"), SKColor.Parse("#D97706"), SKColor.Parse("#1F212B"), SKColor.Parse("#FEF08A"), SKColor.Parse("#1E293B"), false, false, false, false),
        GbcConsoleSkin.Kiwi => new("Kiwi", SKColor.Parse("#84CC16"), SKColor.Parse("#4D7C0F"), SKColor.Parse("#1F212B"), SKColor.Parse("#D9F99D"), SKColor.Parse("#1E293B"), false, false, false, false),
        GbcConsoleSkin.Teal => new("Teal", SKColor.Parse("#06B6D4"), SKColor.Parse("#0E7490"), SKColor.Parse("#1F212B"), SKColor.Parse("#67E8F9"), SKColor.Parse("#F8FAFC"), false, false, false, false),
        GbcConsoleSkin.Grape => new("Grape", SKColor.Parse("#6D28D9"), SKColor.Parse("#3B0764"), SKColor.Parse("#1F212B"), SKColor.Parse("#C4B5FD"), SKColor.Parse("#F8FAFC"), false, false, false, false),
        GbcConsoleSkin.MidnightGold => new("Midnight Gold", SKColor.Parse("#1E2235"), SKColor.Parse("#0F121D"), SKColor.Parse("#141724"), SKColor.Parse("#F59E0B"), SKColor.Parse("#FDE68A"), false, false, false, false),
        GbcConsoleSkin.StormNeonGbc => new("STORM Neon GBC", SKColor.Parse("#16112E"), SKColor.Parse("#090617"), SKColor.Parse("#0A0914"), SKColor.Parse("#7B42F6"), SKColor.Parse("#C4B5FD"), false, false, false, true),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#7B42F6"), SKColors.White, false, false, false, false),
    };

    public static ConsoleSkinPalette GetGbaPalette(GbaConsoleSkin skin) => skin switch
    {
        GbaConsoleSkin.GbaIndigo => new("GBA Indigo", SKColor.Parse("#5B52D6"), SKColor.Parse("#3730A3"), SKColor.Parse("#1B1E28"), SKColor.Parse("#818CF8"), SKColor.Parse("#E2E8F0"), false, false, false, false),
        GbaConsoleSkin.GbaGlacier => new("GBA Glacier", SKColor.Parse("#A5C4D4"), SKColor.Parse("#6B93A8"), SKColor.Parse("#1B1E28"), SKColor.Parse("#E0F2FE"), SKColor.Parse("#1E293B"), true, false, false, false),
        GbaConsoleSkin.GbaArcticWhite => new("GBA Arctic White", SKColor.Parse("#F1F5F9"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#272B38"), SKColor.Parse("#94A3B8"), SKColor.Parse("#1E293B"), false, false, false, false),
        GbaConsoleSkin.GbaSpiceOrange => new("GBA Spice Orange", SKColor.Parse("#F97316"), SKColor.Parse("#C2410C"), SKColor.Parse("#1E1F26"), SKColor.Parse("#FED7AA"), SKColor.Parse("#FFF7ED"), false, false, false, false),
        GbaConsoleSkin.GbaFuchsia => new("GBA Fuchsia", SKColor.Parse("#EC4899"), SKColor.Parse("#9D174D"), SKColor.Parse("#1F1E28"), SKColor.Parse("#FBCFE8"), SKColor.Parse("#FDF2F8"), true, false, false, false),
        GbaConsoleSkin.GbaSpCobalt => new("GBA SP Cobalt", SKColor.Parse("#1D4ED8"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#161922"), SKColor.Parse("#60A5FA"), SKColor.Parse("#EFF6FF"), false, true, false, false),
        GbaConsoleSkin.GbaSpOnyx => new("GBA SP Onyx", SKColor.Parse("#27272A"), SKColor.Parse("#09090B"), SKColor.Parse("#121216"), SKColor.Parse("#A1A1AA"), SKColor.Parse("#F4F4F5"), false, true, false, false),
        GbaConsoleSkin.GbaSpFlameRed => new("GBA SP Flame Red", SKColor.Parse("#DC2626"), SKColor.Parse("#7F1D1D"), SKColor.Parse("#18181B"), SKColor.Parse("#FCA5A5"), SKColor.Parse("#FEF2F2"), false, true, false, false),
        GbaConsoleSkin.GbmFamicom => new("GB Micro Famicom", SKColor.Parse("#B91C1C"), SKColor.Parse("#7F1D1D"), SKColor.Parse("#D4AF37"), SKColor.Parse("#FDE047"), SKColor.Parse("#FEF9C3"), false, false, true, false),
        GbaConsoleSkin.StormNeonGba => new("STORM Neon GBA", SKColor.Parse("#12152E"), SKColor.Parse("#080A18"), SKColor.Parse("#090B15"), SKColor.Parse("#4F46E5"), SKColor.Parse("#818CF8"), false, false, false, true),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#4F46E5"), SKColors.White, false, false, false, false),
    };

    public static bool IsSkinNone(StormPlatform platform, GbcaAppSettings settings) => platform switch
    {
        StormPlatform.Gb or StormPlatform.Sgb => settings.GbConsoleSkin == GbConsoleSkin.None,
        StormPlatform.Gbc => settings.GbcConsoleSkin == GbcConsoleSkin.None,
        _ => settings.GbaConsoleSkin == GbaConsoleSkin.None,
    };

    public static ConsoleSkinPalette ResolvePalette(StormPlatform platform, GbcaAppSettings settings) => platform switch
    {
        StormPlatform.Gb or StormPlatform.Sgb => GetGbPalette(settings.GbConsoleSkin),
        StormPlatform.Gbc => GetGbcPalette(settings.GbcConsoleSkin),
        _ => GetGbaPalette(settings.GbaConsoleSkin),
    };

    /// <summary>
    /// Computes the exact LCD viewport rectangle inside a container of size (width, height),
    /// honoring per-system custom screen size, position, and rotation settings.
    /// </summary>
    public static Rect ComputeScreenViewport(
        double width,
        double height,
        int nativeW,
        int nativeH,
        StormPlatform platform,
        GbcaAppSettings settings)
    {
        var overlay = settings.GetOverlayProfileForPlatform(platform);
        var custom = overlay.ScreenRect;
        bool rotated = custom != null && (custom.RotationDeg == 90 || custom.RotationDeg == 270);
        double srcW = Math.Max(1, rotated ? nativeH : nativeW);
        double srcH = Math.Max(1, rotated ? nativeW : nativeH);
        double aspect = srcW / srcH;

        if (custom != null && custom.UseCustom)
        {
            double boxW = Math.Max(80, width * Math.Clamp(custom.WidthFraction, 0.25f, 1.0f));
            double boxH = Math.Max(64, height * Math.Clamp(custom.HeightFraction, 0.20f, 1.0f));
            double cDrawW, cDrawH;
            if (settings.ScaleMode == GbcaScaleMode.StretchFill)
            {
                cDrawW = boxW;
                cDrawH = boxH;
            }
            else if (settings.ScaleMode == GbcaScaleMode.IntegerPixelPerfect)
            {
                int k = Math.Max(1, (int)Math.Floor(Math.Min(boxW / srcW, boxH / srcH)));
                cDrawW = Math.Min(boxW, srcW * k);
                cDrawH = Math.Min(boxH, srcH * k);
            }
            else
            {
                if (boxW / boxH > aspect)
                {
                    cDrawH = boxH;
                    cDrawW = cDrawH * aspect;
                }
                else
                {
                    cDrawW = boxW;
                    cDrawH = cDrawW / aspect;
                }
            }
            double cx = width * Math.Clamp(custom.CenterX, 0.10f, 0.90f);
            double cy = height * Math.Clamp(custom.CenterY, 0.10f, 0.90f);
            double left = Math.Clamp(cx - cDrawW * 0.5, 0, Math.Max(0, width - cDrawW));
            double top = Math.Clamp(cy - cDrawH * 0.5, 0, Math.Max(0, height - cDrawH));
            return new Rect(Math.Round(left), Math.Round(top), Math.Round(cDrawW), Math.Round(cDrawH));
        }

        bool none = IsSkinNone(platform, settings);
        double deckReserve = overlay.ShowControlsDeck ? Math.Clamp(height * 0.20, 76, 145) : 14;

        double padX = none ? 14 : Math.Clamp(width * 0.08, 28, 120);
        double padTop = none ? 12 : Math.Clamp(height * 0.055, 22, 54);
        double padBottom = none ? (overlay.ShowControlsDeck ? deckReserve : 12) : Math.Max(deckReserve, 46);

        double availW = Math.Max(80, width - padX * 2);
        double availH = Math.Max(64, height - padTop - padBottom);

        double drawW, drawH;
        if (settings.ScaleMode == GbcaScaleMode.StretchFill && none)
        {
            drawW = availW;
            drawH = availH;
        }
        else if (settings.ScaleMode == GbcaScaleMode.IntegerPixelPerfect)
        {
            int k = Math.Max(1, (int)Math.Floor(Math.Min(availW / srcW, availH / srcH)));
            drawW = Math.Min(availW, srcW * k);
            drawH = Math.Min(availH, srcH * k);
        }
        else
        {
            if (availW / availH > aspect)
            {
                drawH = availH;
                drawW = drawH * aspect;
            }
            else
            {
                drawW = availW;
                drawH = drawW / aspect;
            }
        }

        double x = Math.Round((width - drawW) * 0.5);
        double y = Math.Round(padTop + (availH - drawH) * 0.5);
        return new Rect(x, y, Math.Round(drawW), Math.Round(drawH));
    }

    public static WriteableBitmap RenderChassisBitmap(
        int width,
        int height,
        Rect screenRect,
        StormPlatform platform,
        ConsoleSkinPalette palette,
        bool nightDimming)
    {
        int w = Math.Max(64, width);
        int h = Math.Max(64, height);
        var wb = new WriteableBitmap(new PixelSize(w, h), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);

        using var locked = wb.Lock();
        var info = new SKImageInfo(w, h, SKColorType.Bgra8888, SKAlphaType.Premul);
        using var surface = SKSurface.Create(info, locked.Address, locked.RowBytes);
        var canvas = surface.Canvas;
        canvas.Clear(SKColors.Transparent);

        DrawChassis(canvas, w, h, screenRect, platform, palette, nightDimming, isPreview: false);
        canvas.Flush();
        return wb;
    }

    public static WriteableBitmap RenderPreviewBitmap(
        int width,
        int height,
        StormPlatform platform,
        ConsoleSkinPalette palette)
    {
        var wb = new WriteableBitmap(new PixelSize(width, height), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);
        using var locked = wb.Lock();
        var info = new SKImageInfo(width, height, SKColorType.Bgra8888, SKAlphaType.Premul);
        using var surface = SKSurface.Create(info, locked.Address, locked.RowBytes);
        var canvas = surface.Canvas;
        canvas.Clear(SKColor.Parse("#0B0D16"));

        bool isWide = platform == StormPlatform.Gba && !palette.IsSpClamshell;
        double padX = isWide ? width * 0.14 : width * 0.22;
        double padY = height * 0.16;
        var screenRect = new Rect(padX, padY, width - padX * 2, height - padY * 2.3);
        DrawChassis(canvas, width, height, screenRect, platform, palette, nightDimming: false, isPreview: true);
        canvas.Flush();
        return wb;
    }

    private static void DrawChassis(
        SKCanvas canvas,
        int w,
        int h,
        Rect screenRect,
        StormPlatform platform,
        ConsoleSkinPalette p,
        bool nightDimming,
        bool isPreview)
    {
        if (p.DisplayName == "None")
        {
            if (isPreview)
            {
                using var nonePaint = new SKPaint { Color = SKColor.Parse("#1F2937"), Style = SKPaintStyle.Stroke, StrokeWidth = 2, IsAntialias = true };
                canvas.DrawRoundRect(new SKRoundRect(new SKRect(12, 12, w - 12, h - 12), 10), nonePaint);
                using var txtFont = new SKFont(SKTypeface.Default, 13f);
                using var txtPaint = new SKPaint { Color = SKColor.Parse("#94A3B8"), IsAntialias = true };
                canvas.DrawText("БЕЗ КОРПУСА", w * 0.5f, h * 0.54f, SKTextAlign.Center, txtFont, txtPaint);
            }
            return;
        }

        float sx = (float)screenRect.X;
        float sy = (float)screenRect.Y;
        float sw = (float)screenRect.Width;
        float sh = (float)screenRect.Height;

        float margin = isPreview ? 8f : 14f;
        float bezelPadX = isPreview ? 12f : Math.Clamp(sw * 0.09f, 20f, 54f);
        float bezelPadTop = isPreview ? 10f : Math.Clamp(sh * 0.08f, 16f, 38f);
        float bezelPadBottom = isPreview ? 14f : Math.Clamp(sh * 0.11f, 22f, 44f);

        float shellLeft = Math.Max(margin, sx - bezelPadX - (isPreview ? 6 : 22));
        float shellTop = Math.Max(margin, sy - bezelPadTop - (isPreview ? 4 : 14));
        float shellRight = Math.Min(w - margin, sx + sw + bezelPadX + (isPreview ? 6 : 22));
        float shellBottom = Math.Min(h - margin, sy + sh + bezelPadBottom + (isPreview ? 18 : 26));
        float shellRadius = p.IsSpClamshell ? 18f : (p.IsMicro ? 22f : 26f);

        var shellRect = new SKRect(shellLeft, shellTop, shellRight, shellBottom);
        var shellRr = new SKRoundRect(shellRect, shellRadius, shellRadius);

        // 1. Drop shadow behind 3D chassis
        using (var shadowPaint = new SKPaint
        {
            Color = new SKColor((byte)(0), (byte)(0), (byte)(0), (byte)(150)),
            MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, isPreview ? 5f : 14f),
            IsAntialias = true
        })
        {
            var shRect = shellRect;
            shRect.Offset(0, isPreview ? 3f : 8f);
            canvas.DrawRoundRect(new SKRoundRect(shRect, shellRadius), shadowPaint);
        }

        // 2. Main 3D Metallic/Plastic Shell Gradient
        using (var bodyPaint = new SKPaint
        {
            IsAntialias = true,
            Shader = SKShader.CreateLinearGradient(
                new SKPoint(shellLeft, shellTop),
                new SKPoint(shellRight, shellBottom),
                [p.BodyTop, p.BodyBottom],
                null,
                SKShaderTileMode.Clamp)
        })
        {
            canvas.DrawRoundRect(shellRr, bodyPaint);
        }

        // 3. Translucent shell internal PCB trace detail (Atomic Purple / Glacier / Fuchsia)
        if (p.IsTranslucent)
        {
            using var pcbPaint = new SKPaint
            {
                Color = new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(45)),
                Style = SKPaintStyle.Stroke,
                StrokeWidth = 1.4f,
                IsAntialias = true
            };
            for (float tx = shellLeft + 18; tx < shellRight - 18; tx += 22)
            {
                canvas.DrawLine(tx, shellTop + 10, tx + 8, shellTop + 26, pcbPaint);
                canvas.DrawCircle(tx + 8, shellTop + 26, 2.2f, pcbPaint);
            }
        }

        // 4. 3D Outer Bevel Highlight & Neon Edge
        using (var rimPaint = new SKPaint
        {
            Style = SKPaintStyle.Stroke,
            StrokeWidth = p.IsNeon ? 2.2f : 1.5f,
            IsAntialias = true,
            Color = p.IsNeon ? p.AccentColor : new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(95))
        })
        {
            var inset = shellRect;
            inset.Inflate(-1.5f, -1.5f);
            canvas.DrawRoundRect(new SKRoundRect(inset, shellRadius - 1f), rimPaint);
        }

        if (p.IsSpClamshell)
        {
            // Clamshell hinge cylinder across bottom edge of screen chassis
            float hingeY = shellBottom - (isPreview ? 10 : 18);
            using var hingePaint = new SKPaint
            {
                Shader = SKShader.CreateLinearGradient(
                    new SKPoint(shellLeft, hingeY - 6),
                    new SKPoint(shellLeft, hingeY + 6),
                    [new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(120)), new SKColor((byte)(0), (byte)(0), (byte)(0), (byte)(170))],
                    null,
                    SKShaderTileMode.Clamp),
                IsAntialias = true
            };
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellLeft + 12, hingeY - 5, shellRight - 12, hingeY + 5), 5), hingePaint);
        }

        // 5. Recessed Dark Glass Screen Lens Bezel
        var bezelRect = new SKRect(
            sx - bezelPadX,
            sy - bezelPadTop,
            sx + sw + bezelPadX,
            sy + sh + bezelPadBottom);
        var bezelRr = new SKRoundRect(bezelRect, 14f, 14f);

        using (var bezelPaint = new SKPaint
        {
            Color = p.BezelColor,
            IsAntialias = true
        })
        {
            canvas.DrawRoundRect(bezelRr, bezelPaint);
        }

        using (var bezelRim = new SKPaint
        {
            Color = p.IsNeon ? p.AccentColor.WithAlpha(160) : new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(75)),
            Style = SKPaintStyle.Stroke,
            StrokeWidth = 1.4f,
            IsAntialias = true
        })
        {
            canvas.DrawRoundRect(bezelRr, bezelRim);
        }

        // 6. Power LED (Red/Green/Cyan indicator on left bezel)
        float ledX = bezelRect.Left + (isPreview ? 6f : 14f);
        float ledY = bezelRect.Top + bezelRect.Height * 0.34f;
        SKColor ledCol = p.IsNeon ? p.AccentColor : SKColor.Parse("#EF4444");
        if (nightDimming) ledCol = ledCol.WithAlpha(110);

        using (var glowPaint = new SKPaint
        {
            Color = ledCol.WithAlpha(nightDimming ? (byte)60 : (byte)160),
            MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, isPreview ? 3f : 7f),
            IsAntialias = true
        })
        {
            canvas.DrawCircle(ledX, ledY, isPreview ? 3.5f : 6.5f, glowPaint);
        }
        using (var ledCore = new SKPaint { Color = ledCol, IsAntialias = true })
        {
            canvas.DrawCircle(ledX, ledY, isPreview ? 1.8f : 3.2f, ledCore);
        }

        // 7. Branding text inside bottom bezel
        string brandLabel = platform switch
        {
            StormPlatform.Gb or StormPlatform.Sgb => "GAME BOY",
            StormPlatform.Gbc => "GAME BOY COLOR",
            _ => p.IsMicro ? "GAME BOY MICRO" : (p.IsSpClamshell ? "GAME BOY ADVANCE SP" : "GAME BOY ADVANCE"),
        };
        using (var brandTypeface = SKTypeface.FromFamilyName("Segoe UI", SKFontStyleWeight.Bold, SKFontStyleWidth.Normal, SKFontStyleSlant.Italic))
        using (var brandFont = new SKFont(brandTypeface, isPreview ? 7.5f : Math.Clamp(bezelPadBottom * 0.44f, 11f, 16f)))
        using (var brandPaint = new SKPaint { Color = p.IsNeon ? p.AccentColor : SKColor.Parse("#CBD5E1"), IsAntialias = true })
        {
            canvas.DrawText(brandLabel, bezelRect.MidX, bezelRect.Bottom - (isPreview ? 4f : bezelPadBottom * 0.28f), SKTextAlign.Center, brandFont, brandPaint);
        }

        // 8. Recessed LCD Inner Shadow or Preview Screen Mockup
        var lcdRect = new SKRect(sx, sy, sx + sw, sy + sh);
        if (isPreview)
        {
            using var lcdFill = new SKPaint
            {
                Shader = SKShader.CreateLinearGradient(
                    new SKPoint(lcdRect.Left, lcdRect.Top),
                    new SKPoint(lcdRect.Right, lcdRect.Bottom),
                    [SKColor.Parse("#1E293B"), SKColor.Parse("#0F172A")],
                    null,
                    SKShaderTileMode.Clamp),
                IsAntialias = true
            };
            canvas.DrawRect(lcdRect, lcdFill);
            using var dotFont = new SKFont(SKTypeface.Default, 9f);
            using var dotPaint = new SKPaint { Color = p.AccentColor, IsAntialias = true };
            canvas.DrawText("STORM", lcdRect.MidX, lcdRect.MidY + 3, SKTextAlign.Center, dotFont, dotPaint);
        }
        else
        {
            using var lcdCutout = new SKPaint
            {
                Color = SKColor.Parse("#05060A"),
                IsAntialias = false
            };
            canvas.DrawRect(lcdRect, lcdCutout);
        }

        // 9. Speaker Slits on right bottom chassis
        if (!isPreview)
        {
            using var slitPaint = new SKPaint
            {
                Color = new SKColor((byte)(0), (byte)(0), (byte)(0), (byte)(130)),
                StrokeWidth = 3f,
                StrokeCap = SKStrokeCap.Round,
                IsAntialias = true
            };
            float spX = shellRight - 44f;
            float spY = shellBottom - 20f;
            for (int i = 0; i < 4; i++)
            {
                float ox = i * 8f;
                canvas.DrawLine(spX + ox, spY, spX + ox + 6f, spY + 11f, slitPaint);
            }
        }

        // 10. Optional Night Dimming veil over chassis (never dims LCD itself)
        if (nightDimming && !isPreview)
        {
            using var dimPaint = new SKPaint { Color = new SKColor((byte)(4), (byte)(6), (byte)(12), (byte)(105)), IsAntialias = true };
            canvas.DrawRoundRect(shellRr, dimPaint);
        }
    }
}

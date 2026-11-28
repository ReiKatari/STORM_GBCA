// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using SkiaSharp;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;

namespace StormGbca.UI.Views.Emulation;

public sealed record ButtonStylePalette(
    string DisplayName,
    SKColor DeckFill,
    SKColor DpadColor,
    SKColor ButtonAFill,
    SKColor ButtonBFill,
    SKColor TriggerFill,
    SKColor StartSelectFill,
    SKColor GlyphColor,
    SKColor GlowColor
);

public readonly record struct OverlayHitbox(StormKeyMask Key, Rect Bounds);

/// <summary>
/// Per-System Overlay & Interactive Button Deck renderer using SkiaSharp.
/// Renders selectable bezel/deck styles and authentic GB/GBC/GBA buttons (hiding L/R on GB/GBC by default).
/// </summary>
public static class GbcaOverlayRenderer
{
    public static ButtonStylePalette GetButtonPalette(OverlayButtonStyle style) => style switch
    {
        OverlayButtonStyle.DmgMagenta => new("DMG Magenta", new SKColor((byte)(28), (byte)(28), (byte)(36), (byte)(150)), SKColor.Parse("#1F232B"), SKColor.Parse("#9D174D"), SKColor.Parse("#9D174D"), SKColor.Parse("#6B7280"), SKColor.Parse("#4B5563"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#EC4899")),
        OverlayButtonStyle.GbcAtomicPurple => new("GBC Atomic Purple", new SKColor((byte)(76), (byte)(29), (byte)(149), (byte)(145)), SKColor.Parse("#1E1B4B"), SKColor.Parse("#312E81"), SKColor.Parse("#312E81"), SKColor.Parse("#4C1D95"), SKColor.Parse("#374151"), SKColor.Parse("#E9D5FF"), SKColor.Parse("#A855F7")),
        OverlayButtonStyle.GbaIndigo => new("GBA Indigo", new SKColor((byte)(49), (byte)(46), (byte)(129), (byte)(150)), SKColor.Parse("#CBD5E1"), SKColor.Parse("#94A3B8"), SKColor.Parse("#94A3B8"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#64748B"), SKColor.Parse("#1E293B"), SKColor.Parse("#818CF8")),
        OverlayButtonStyle.GbaGlacier => new("GBA Glacier", new SKColor((byte)(107), (byte)(147), (byte)(168), (byte)(135)), SKColor.Parse("#E2E8F0"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#F1F5F9"), SKColor.Parse("#94A3B8"), SKColor.Parse("#0F172A"), SKColor.Parse("#38BDF8")),
        OverlayButtonStyle.GbaSpCobalt => new("GBA SP Cobalt", new SKColor((byte)(30), (byte)(58), (byte)(138), (byte)(155)), SKColor.Parse("#1E293B"), SKColor.Parse("#334155"), SKColor.Parse("#334155"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#475569"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#60A5FA")),
        OverlayButtonStyle.GbmFamicom => new("GBM Famicom", new SKColor((byte)(153), (byte)(27), (byte)(27), (byte)(160)), SKColor.Parse("#27272A"), SKColor.Parse("#D4AF37"), SKColor.Parse("#D4AF37"), SKColor.Parse("#B45309"), SKColor.Parse("#52525B"), SKColor.Parse("#18181B"), SKColor.Parse("#FACC15")),
        OverlayButtonStyle.StormNeon => new("STORM Neon", new SKColor((byte)(14), (byte)(16), (byte)(32), (byte)(150)), SKColor.Parse("#1E293B"), SKColor.Parse("#6366F1"), SKColor.Parse("#8B5CF6"), SKColor.Parse("#06B6D4"), SKColor.Parse("#334155"), SKColor.Parse("#FFFFFF"), SKColor.Parse("#22D3EE")),
        OverlayButtonStyle.MatteDark => new("Matte Dark", new SKColor((byte)(18), (byte)(20), (byte)(26), (byte)(165)), SKColor.Parse("#272A35"), SKColor.Parse("#323644"), SKColor.Parse("#323644"), SKColor.Parse("#272A35"), SKColor.Parse("#1F222D"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#94A3B8")),
        OverlayButtonStyle.CrystalGlass => new("Crystal Glass", new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(90)), new SKColor((byte)(226), (byte)(232), (byte)(240), (byte)(135)), new SKColor((byte)(56), (byte)(189), (byte)(248), (byte)(150)), new SKColor((byte)(168), (byte)(85), (byte)(247), (byte)(150)), new SKColor((byte)(203), (byte)(213), (byte)(225), (byte)(135)), new SKColor((byte)(148), (byte)(163), (byte)(184), (byte)(135)), SKColor.Parse("#FFFFFF"), SKColor.Parse("#38BDF8")),
        OverlayButtonStyle.Cyberpunk => new("Cyberpunk", new SKColor((byte)(16), (byte)(12), (byte)(28), (byte)(160)), SKColor.Parse("#1F1A38"), SKColor.Parse("#FCEE09"), SKColor.Parse("#FF007F"), SKColor.Parse("#00F0FF"), SKColor.Parse("#312E81"), SKColor.Parse("#0B0A10"), SKColor.Parse("#FF007F")),
        OverlayButtonStyle.Sakura => new("Sakura", new SKColor((byte)(80), (byte)(28), (byte)(52), (byte)(145)), SKColor.Parse("#FBCFE8"), SKColor.Parse("#F472B6"), SKColor.Parse("#EC4899"), SKColor.Parse("#F9A8D4"), SKColor.Parse("#9D174D"), SKColor.Parse("#FFFFFF"), SKColor.Parse("#F472B6")),
        _ => new("STORM Neon", new SKColor((byte)(14), (byte)(16), (byte)(32), (byte)(150)), SKColor.Parse("#1E293B"), SKColor.Parse("#6366F1"), SKColor.Parse("#8B5CF6"), SKColor.Parse("#06B6D4"), SKColor.Parse("#334155"), SKColor.Parse("#FFFFFF"), SKColor.Parse("#22D3EE")),
    };

    public static List<OverlayHitbox> ComputeHitboxes(int width, int height, SystemOverlayProfile profile)
    {
        var boxes = new List<OverlayHitbox>();
        if (!profile.ShowControlsDeck || width < 120 || height < 120) return boxes;

        double s = Math.Clamp(profile.Scale, 0.5, 2.0);
        double deckH = Math.Clamp(height * 0.19 * s, 72, 155);
        double cy = height - deckH * 0.54;

        // D-Pad on left
        double dpadR = Math.Clamp(deckH * 0.34, 24, 50);
        double dpadCx = Math.Clamp(width * 0.085, dpadR + 14, 128);
        double arm = dpadR * 0.72;
        boxes.Add(new(StormKeyMask.Up, new Rect(dpadCx - arm * 0.5, cy - dpadR, arm, arm)));
        boxes.Add(new(StormKeyMask.Down, new Rect(dpadCx - arm * 0.5, cy + dpadR - arm, arm, arm)));
        boxes.Add(new(StormKeyMask.Left, new Rect(dpadCx - dpadR, cy - arm * 0.5, arm, arm)));
        boxes.Add(new(StormKeyMask.Right, new Rect(dpadCx + dpadR - arm, cy - arm * 0.5, arm, arm)));

        // A & B on right (guaranteed zero overlap + configurable spacing & separate A/B mode!)
        double btnR = Math.Clamp(deckH * 0.21, 16, 32);
        double spacingFactor = Math.Clamp(profile.AbSpacingPercent, 95, 200) / 100.0;
        double distX = btnR * (profile.SeparateABButtons ? 2.65 : 2.35) * spacingFactor;
        double abBaseX = width - Math.Clamp(width * 0.105, btnR * 2.8 + 16, 155);
        double bCx = abBaseX - distX * 0.55;
        double aCx = abBaseX + distX * 0.45;
        double bCy = profile.SeparateABButtons ? cy + btnR * 0.30 : cy + btnR * 0.35;
        double aCy = profile.SeparateABButtons ? cy - btnR * 0.55 : cy - btnR * 0.45;
        boxes.Add(new(StormKeyMask.B, new Rect(bCx - btnR, bCy - btnR, btnR * 2, btnR * 2)));
        boxes.Add(new(StormKeyMask.A, new Rect(aCx - btnR, aCy - btnR, btnR * 2, btnR * 2)));

        // Select & Start in center-bottom
        double pillW = Math.Clamp(52 * s, 40, 78);
        double pillH = Math.Clamp(18 * s, 14, 24);
        double midX = width * 0.5;
        double pillY = height - pillH - 10;
        boxes.Add(new(StormKeyMask.Select, new Rect(Math.Max(14, dpadCx - pillW * 0.5), pillY, pillW, pillH)));
        boxes.Add(new(StormKeyMask.Start, new Rect(Math.Min(width - pillW - 14, abBaseX - pillW * 0.5), pillY, pillW, pillH)));

        // L & R shoulder triggers (only when ShowLR is true, e.g. GBA!)
        if (profile.ShowLR)
        {
            double trigW = Math.Clamp(68 * s, 50, 96);
            double trigH = Math.Clamp(22 * s, 18, 30);
            double trigY = height - deckH - trigH * 0.4;
            boxes.Add(new(StormKeyMask.L, new Rect(18, trigY, trigW, trigH)));
            boxes.Add(new(StormKeyMask.R, new Rect(width - trigW - 18, trigY, trigW, trigH)));
        }

        return boxes;
    }

    public static WriteableBitmap RenderOverlayBitmap(
        int width,
        int height,
        Rect screenRect,
        StormPlatform platform,
        SystemOverlayProfile profile,
        uint pressedMask)
    {
        int w = Math.Max(64, width);
        int h = Math.Max(64, height);
        var wb = new WriteableBitmap(new PixelSize(w, h), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);

        using var locked = wb.Lock();
        var info = new SKImageInfo(w, h, SKColorType.Bgra8888, SKAlphaType.Premul);
        using var surface = SKSurface.Create(info, locked.Address, locked.RowBytes);
        var canvas = surface.Canvas;
        canvas.Clear(SKColors.Transparent);

        byte alpha = (byte)Math.Clamp((int)(profile.Opacity * 255f), 25, 255);
        var pal = GetButtonPalette(profile.ButtonStyle);

        // 1. Optional LCD Bezel Frame Overlay around screenRect
        DrawBezelOverlay(canvas, screenRect, profile.OverlayLayout, pal, alpha);

        // 2. Interactive / Live Status Button Deck
        if (profile.ShowControlsDeck)
        {
            var hitboxes = ComputeHitboxes(w, h, profile);
            DrawButtonDeck(canvas, w, h, profile, pal, hitboxes, pressedMask, alpha, platform);
        }

        canvas.Flush();
        return wb;
    }

    public static WriteableBitmap RenderPreviewBitmap(int width, int height, StormPlatform platform, SystemOverlayProfile profile)
    {
        var wb = new WriteableBitmap(new PixelSize(width, height), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);
        using var locked = wb.Lock();
        var info = new SKImageInfo(width, height, SKColorType.Bgra8888, SKAlphaType.Premul);
        using var surface = SKSurface.Create(info, locked.Address, locked.RowBytes);
        var canvas = surface.Canvas;
        canvas.Clear(SKColor.Parse("#0B0D16"));

        var pal = GetButtonPalette(profile.ButtonStyle);
        var screenRect = new Rect(18, 10, width - 36, height * 0.50);
        DrawBezelOverlay(canvas, screenRect, profile.OverlayLayout, pal, 240);

        if (profile.ShowControlsDeck)
        {
            var miniProfile = new SystemOverlayProfile
            {
                OverlayLayout = profile.OverlayLayout,
                ButtonStyle = profile.ButtonStyle,
                Opacity = profile.Opacity,
                Scale = 0.72f,
                ShowControlsDeck = true,
                ShowLR = profile.ShowLR,
                SeparateABButtons = profile.SeparateABButtons,
                AbSpacingPercent = profile.AbSpacingPercent
            };
            var hitboxes = ComputeHitboxes(width, height, miniProfile);
            DrawButtonDeck(canvas, width, height, miniProfile, pal, hitboxes, (uint)(StormKeyMask.A | StormKeyMask.Right), 240, platform);
        }

        canvas.Flush();
        return wb;
    }

    private static void DrawBezelOverlay(SKCanvas canvas, Rect screenRect, OverlayBezelStyle style, ButtonStylePalette pal, byte alpha)
    {
        if (style == OverlayBezelStyle.None) return;

        var r = new SKRect((float)screenRect.X - 4, (float)screenRect.Y - 4, (float)screenRect.Right + 4, (float)screenRect.Bottom + 4);
        var rr = new SKRoundRect(r, 8f, 8f);

        using var stroke = new SKPaint
        {
            Style = SKPaintStyle.Stroke,
            StrokeWidth = style is OverlayBezelStyle.StormNeonDeck or OverlayBezelStyle.CyberGrid ? 2.4f : 1.5f,
            Color = (style is OverlayBezelStyle.StormNeonDeck or OverlayBezelStyle.CyberGrid ? pal.GlowColor : SKColor.Parse("#94A3B8")).WithAlpha(alpha),
            IsAntialias = true
        };
        canvas.DrawRoundRect(rr, stroke);

        if (style == OverlayBezelStyle.StormNeonDeck)
        {
            using var glow = new SKPaint
            {
                Style = SKPaintStyle.Stroke,
                StrokeWidth = 4f,
                Color = pal.GlowColor.WithAlpha((byte)(alpha / 3)),
                MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, 5f),
                IsAntialias = true
            };
            canvas.DrawRoundRect(rr, glow);
        }
        else if (style == OverlayBezelStyle.CyberGrid)
        {
            using var cornerPaint = new SKPaint { Color = pal.GlowColor.WithAlpha(alpha), StrokeWidth = 3f, IsAntialias = true };
            float cl = 14f;
            canvas.DrawLine(r.Left, r.Top, r.Left + cl, r.Top, cornerPaint);
            canvas.DrawLine(r.Left, r.Top, r.Left, r.Top + cl, cornerPaint);
            canvas.DrawLine(r.Right - cl, r.Top, r.Right, r.Top, cornerPaint);
            canvas.DrawLine(r.Right, r.Top, r.Right, r.Top + cl, cornerPaint);
            canvas.DrawLine(r.Left, r.Bottom, r.Left + cl, r.Bottom, cornerPaint);
            canvas.DrawLine(r.Left, r.Bottom - cl, r.Left, r.Bottom, cornerPaint);
            canvas.DrawLine(r.Right - cl, r.Bottom, r.Right, r.Bottom, cornerPaint);
            canvas.DrawLine(r.Right, r.Bottom - cl, r.Right, r.Bottom, cornerPaint);
        }
    }

    private static void DrawButtonDeck(
        SKCanvas canvas,
        int w,
        int h,
        SystemOverlayProfile profile,
        ButtonStylePalette pal,
        List<OverlayHitbox> hitboxes,
        uint pressedMask,
        byte alpha,
        StormPlatform platform)
    {
        if (profile.OverlayLayout is OverlayBezelStyle.GlassDeck or OverlayBezelStyle.StormNeonDeck or OverlayBezelStyle.RetroPlastic)
        {
            float deckH = (float)Math.Clamp(h * 0.19 * profile.Scale, 60, 150);
            var deckRect = new SKRect(10, h - deckH - 4, w - 10, h - 4);
            var deckColor = pal.DeckFill.WithAlpha((byte)Math.Clamp((int)(pal.DeckFill.Alpha * (alpha / 255f)), 25, 210));
            using var deckPaint = new SKPaint { Color = deckColor, IsAntialias = true };
            canvas.DrawRoundRect(new SKRoundRect(deckRect, 14f), deckPaint);

            using var rim = new SKPaint
            {
                Style = SKPaintStyle.Stroke,
                StrokeWidth = 1.2f,
                Color = pal.GlowColor.WithAlpha((byte)(alpha * 0.45)),
                IsAntialias = true
            };
            canvas.DrawRoundRect(new SKRoundRect(deckRect, 14f), rim);
        }

        foreach (var box in hitboxes)
        {
            bool pressed = (pressedMask & (uint)box.Key) != 0;
            var r = new SKRect((float)box.Bounds.X, (float)box.Bounds.Y, (float)box.Bounds.Right, (float)box.Bounds.Bottom);

            if (box.Key is StormKeyMask.A or StormKeyMask.B)
            {
                SKColor fill = box.Key == StormKeyMask.A ? pal.ButtonAFill : pal.ButtonBFill;
                if (pressed) fill = pal.GlowColor;
                DrawRoundButton(canvas, r.MidX, r.MidY, r.Width * 0.46f, fill.WithAlpha(alpha), pal.GlyphColor.WithAlpha(alpha), box.Key == StormKeyMask.A ? "A" : "B", pressed, pal.GlowColor);
            }
            else if (box.Key is StormKeyMask.Start or StormKeyMask.Select)
            {
                SKColor fill = pressed ? pal.GlowColor : pal.StartSelectFill;
                string label = box.Key == StormKeyMask.Start ? "START" : "SELECT";
                DrawPillButton(canvas, r, fill.WithAlpha(alpha), pal.GlyphColor.WithAlpha(alpha), label, pressed, pal.GlowColor);
            }
            else if (box.Key is StormKeyMask.L or StormKeyMask.R)
            {
                SKColor fill = pressed ? pal.GlowColor : pal.TriggerFill;
                string label = box.Key == StormKeyMask.L ? "L" : "R";
                DrawPillButton(canvas, r, fill.WithAlpha(alpha), pal.GlyphColor.WithAlpha(alpha), label, pressed, pal.GlowColor);
            }
            else
            {
                SKColor fill = pressed ? pal.GlowColor : pal.DpadColor;
                string arrow = box.Key switch
                {
                    StormKeyMask.Up => "^",
                    StormKeyMask.Down => "v",
                    StormKeyMask.Left => "<",
                    _ => ">",
                };
                DrawDpadSegment(canvas, r, fill.WithAlpha(alpha), pal.GlyphColor.WithAlpha(alpha), arrow, pressed);
            }
        }
    }

    private static void DrawRoundButton(SKCanvas canvas, float cx, float cy, float radius, SKColor fill, SKColor glyphCol, string text, bool pressed, SKColor glow)
    {
        if (pressed)
        {
            using var gp = new SKPaint { Color = glow.WithAlpha(160), MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, 6f), IsAntialias = true };
            canvas.DrawCircle(cx, cy, radius + 3f, gp);
        }
        using (var p = new SKPaint
        {
            Shader = SKShader.CreateRadialGradient(new SKPoint(cx - radius * 0.25f, cy - radius * 0.25f), radius * 1.3f, [fill, new SKColor((byte)(fill.Red * 0.65), (byte)(fill.Green * 0.65), (byte)(fill.Blue * 0.65), fill.Alpha)], SKShaderTileMode.Clamp),
            IsAntialias = true
        })
        {
            canvas.DrawCircle(cx, cy, radius, p);
        }
        using var border = new SKPaint { Style = SKPaintStyle.Stroke, StrokeWidth = 1.4f, Color = new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(140)), IsAntialias = true };
        canvas.DrawCircle(cx, cy, radius, border);

        using var tf = SKTypeface.FromFamilyName("Segoe UI", SKFontStyleWeight.Bold, SKFontStyleWidth.Normal, SKFontStyleSlant.Upright);
        using var font = new SKFont(tf, Math.Max(10f, radius * 0.92f));
        using var tp = new SKPaint { Color = glyphCol, IsAntialias = true };
        var fm = font.Metrics;
        float textY = cy - (fm.Ascent + fm.Descent) * 0.5f;
        canvas.DrawText(text, cx, textY, SKTextAlign.Center, font, tp);
    }

    private static void DrawPillButton(SKCanvas canvas, SKRect rect, SKColor fill, SKColor glyphCol, string text, bool pressed, SKColor glow)
    {
        if (pressed)
        {
            using var gp = new SKPaint { Color = glow.WithAlpha(150), MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, 5f), IsAntialias = true };
            canvas.DrawRoundRect(new SKRoundRect(rect, rect.Height * 0.5f), gp);
        }
        using var p = new SKPaint { Color = fill, IsAntialias = true };
        canvas.DrawRoundRect(new SKRoundRect(rect, rect.Height * 0.5f), p);
        using var border = new SKPaint { Style = SKPaintStyle.Stroke, StrokeWidth = 1.1f, Color = new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(130)), IsAntialias = true };
        canvas.DrawRoundRect(new SKRoundRect(rect, rect.Height * 0.5f), border);

        using var tf = SKTypeface.FromFamilyName("Segoe UI", SKFontStyleWeight.Bold, SKFontStyleWidth.Normal, SKFontStyleSlant.Upright);
        using var font = new SKFont(tf, Math.Clamp(rect.Height * 0.56f, 8.5f, 12f));
        using var tp = new SKPaint { Color = glyphCol, IsAntialias = true };
        var fm = font.Metrics;
        float textY = rect.MidY - (fm.Ascent + fm.Descent) * 0.5f;
        canvas.DrawText(text, rect.MidX, textY, SKTextAlign.Center, font, tp);
    }

    private static void DrawDpadSegment(SKCanvas canvas, SKRect rect, SKColor fill, SKColor glyphCol, string arrow, bool pressed)
    {
        using var p = new SKPaint { Color = fill, IsAntialias = true };
        canvas.DrawRoundRect(new SKRoundRect(rect, 5f), p);
        using var border = new SKPaint { Style = SKPaintStyle.Stroke, StrokeWidth = pressed ? 1.6f : 1.0f, Color = new SKColor((byte)(255), (byte)(255), (byte)(255), (byte)(110)), IsAntialias = true };
        canvas.DrawRoundRect(new SKRoundRect(rect, 5f), border);

        using var tf = SKTypeface.FromFamilyName("Consolas", SKFontStyleWeight.Bold, SKFontStyleWidth.Normal, SKFontStyleSlant.Upright);
        using var font = new SKFont(tf, Math.Clamp(rect.Height * 0.56f, 9f, 13f));
        using var tp = new SKPaint { Color = glyphCol, IsAntialias = true };
        var fm = font.Metrics;
        float textY = rect.MidY - (fm.Ascent + fm.Descent) * 0.5f;
        canvas.DrawText(arrow, rect.MidX, textY, SKTextAlign.Center, font, tp);
    }
}

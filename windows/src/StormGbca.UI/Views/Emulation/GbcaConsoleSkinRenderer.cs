// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using SkiaSharp;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Emulation;

public enum HardwareShellForm
{
    Dmg = 0,
    Pocket = 1,
    Light = 2,
    Sgb = 3,
    Gbc = 4,
    Agb = 5,
    GbaSp = 6,
    GbMicro = 7,
    Aurora = 8,
}

public sealed record ConsoleSkinPalette(
    string DisplayName,
    SKColor BodyTop,
    SKColor BodyBottom,
    SKColor BezelOuter,
    SKColor BezelInner,
    SKColor AccentColor,
    SKColor SecondaryTrim,
    SKColor TextColor,
    SKColor PowerLedColor,
    HardwareShellForm Form,
    bool IsTranslucent,
    bool IsMetallic,
    string BrandTitle
)
{
    public bool IsSpClamshell => Form == HardwareShellForm.GbaSp;
    public bool IsMicro => Form == HardwareShellForm.GbMicro;
    public bool IsNeon => Form == HardwareShellForm.Aurora;
}

/// <summary>
/// SkiaSharp photorealistic 3D console chassis renderer for GB (12 + None), GBC (12 + None), and GBA (16 + None).
/// Authentically renders DMG-01 stripes/cutaway/vents, Pocket mirror bezel, Light EL glow, SGB SNES frame,
/// GBC 5-color COLOR branding & IR notch, AGB-001 shoulder wings, GBA SP clamshell hinge/4 bumpers/16-hole diamond speaker,
/// GB Micro brushed faceplate, and translucent PCB/CPU internals.
/// </summary>
public static class GbcaConsoleSkinRenderer
{
    public static ConsoleSkinPalette GetGbPalette(GbConsoleSkin skin) => skin switch
    {
        GbConsoleSkin.DmgClassicGray => new("DMG-01 Classic Gray", SKColor.Parse("#DAD6CA"), SKColor.Parse("#B5B0A1"), SKColor.Parse("#6B6F7B"), SKColor.Parse("#4C505C"), SKColor.Parse("#8C1D4F"), SKColor.Parse("#23327A"), SKColor.Parse("#DCE2EC"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
        GbConsoleSkin.DmgPlayItLoudBlack => new("DMG Play It Loud Black", SKColor.Parse("#23262F"), SKColor.Parse("#101218"), SKColor.Parse("#3E4352"), SKColor.Parse("#1C1F27"), SKColor.Parse("#E11D48"), SKColor.Parse("#2563EB"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
        GbConsoleSkin.DmgPlayItLoudRed => new("DMG Play It Loud Red", SKColor.Parse("#DC2626"), SKColor.Parse("#991B1B"), SKColor.Parse("#3D3F4A"), SKColor.Parse("#23252E"), SKColor.Parse("#FCA5A5"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
        GbConsoleSkin.DmgPlayItLoudYellow => new("DMG Play It Loud Yellow", SKColor.Parse("#FACC15"), SKColor.Parse("#CA8A04"), SKColor.Parse("#3D3F4A"), SKColor.Parse("#22242C"), SKColor.Parse("#991B1B"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
        GbConsoleSkin.DmgPlayItLoudGreen => new("DMG Play It Loud Green", SKColor.Parse("#16A34A"), SKColor.Parse("#14532D"), SKColor.Parse("#3B3E49"), SKColor.Parse("#21242D"), SKColor.Parse("#86EFAC"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
        GbConsoleSkin.DmgClearSkeleton => new("DMG Clear Skeleton (PCB)", SKColor.Parse("#C5D0DE"), SKColor.Parse("#8D9CB0"), SKColor.Parse("#5A6577"), SKColor.Parse("#252D3B"), SKColor.Parse("#8C1D4F"), SKColor.Parse("#1E40AF"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#FF2442"), HardwareShellForm.Dmg, true, false, "GAME BOY"),
        GbConsoleSkin.PocketSilver => new("Game Boy Pocket Silver", SKColor.Parse("#E5E9F0"), SKColor.Parse("#9EA8B8"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#475569"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#334155"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#EF4444"), HardwareShellForm.Pocket, false, true, "GAME BOY pocket"),
        GbConsoleSkin.PocketIceBlue => new("Pocket Ice Blue (PCB)", SKColor.Parse("#67D4F5"), SKColor.Parse("#0284C7"), SKColor.Parse("#334155"), SKColor.Parse("#141B29"), SKColor.Parse("#7DD3FC"), SKColor.Parse("#0369A1"), SKColor.Parse("#F0F9FF"), SKColor.Parse("#EF4444"), HardwareShellForm.Pocket, true, false, "GAME BOY pocket"),
        GbConsoleSkin.PocketExtremeGreen => new("Pocket Extreme Green", SKColor.Parse("#4ADE80"), SKColor.Parse("#15803D"), SKColor.Parse("#27342B"), SKColor.Parse("#111A14"), SKColor.Parse("#86EFAC"), SKColor.Parse("#166534"), SKColor.Parse("#ECFDF5"), SKColor.Parse("#EF4444"), HardwareShellForm.Pocket, true, false, "GAME BOY pocket"),
        GbConsoleSkin.GameBoyLightGold => new("Game Boy Light Gold", SKColor.Parse("#F6D365"), SKColor.Parse("#B48119"), SKColor.Parse("#C4A045"), SKColor.Parse("#231D0F"), SKColor.Parse("#2DD4BF"), SKColor.Parse("#0D9488"), SKColor.Parse("#CCFBF1"), SKColor.Parse("#2DD4BF"), HardwareShellForm.Light, false, true, "GAME BOY LIGHT"),
        GbConsoleSkin.SuperGameBoyFrame => new("Super Game Boy (SNES)", SKColor.Parse("#C4C2CC"), SKColor.Parse("#8B8899"), SKColor.Parse("#6D28D9"), SKColor.Parse("#231936"), SKColor.Parse("#A78BFA"), SKColor.Parse("#4C1D95"), SKColor.Parse("#EDE9FE"), SKColor.Parse("#22C55E"), HardwareShellForm.Sgb, false, false, "SUPER GAME BOY"),
        GbConsoleSkin.StormAuroraGb => new("STORM Aurora GB", SKColor.Parse("#0E1526"), SKColor.Parse("#050810"), SKColor.Parse("#00E5FF"), SKColor.Parse("#070A12"), SKColor.Parse("#00E5FF"), SKColor.Parse("#22C55E"), SKColor.Parse("#E0F2FE"), SKColor.Parse("#00E5FF"), HardwareShellForm.Aurora, false, false, "STORM GB AURORA"),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#11131A"), SKColor.Parse("#8BAC0F"), SKColor.Parse("#8BAC0F"), SKColors.White, SKColor.Parse("#EF4444"), HardwareShellForm.Dmg, false, false, "GAME BOY"),
    };

    public static ConsoleSkinPalette GetGbcPalette(GbcConsoleSkin skin) => skin switch
    {
        GbcConsoleSkin.AtomicPurple => new("Atomic Purple (PCB)", SKColor.Parse("#8E63F8"), SKColor.Parse("#4A1D96"), SKColor.Parse("#282438"), SKColor.Parse("#111118"), SKColor.Parse("#C4B5FD"), SKColor.Parse("#3B0764"), SKColor.Parse("#F1F5F9"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, true, false, "GAME BOY"),
        GbcConsoleSkin.Berry => new("Berry Magenta", SKColor.Parse("#E11D6C"), SKColor.Parse("#9D174D"), SKColor.Parse("#2B222C"), SKColor.Parse("#111116"), SKColor.Parse("#F472B6"), SKColor.Parse("#831843"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.Teal => new("Teal", SKColor.Parse("#0EA5A4"), SKColor.Parse("#0F766E"), SKColor.Parse("#1F2B2E"), SKColor.Parse("#101317"), SKColor.Parse("#5EEAD4"), SKColor.Parse("#115E59"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.Kiwi => new("Kiwi Lime", SKColor.Parse("#84CC16"), SKColor.Parse("#4D7C0F"), SKColor.Parse("#23291E"), SKColor.Parse("#111312"), SKColor.Parse("#BEF264"), SKColor.Parse("#3F6212"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.Dandelion => new("Dandelion Yellow", SKColor.Parse("#FACC15"), SKColor.Parse("#D97706"), SKColor.Parse("#2A2618"), SKColor.Parse("#111114"), SKColor.Parse("#FEF08A"), SKColor.Parse("#B45309"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.Grape => new("Grape Purple", SKColor.Parse("#6D28D9"), SKColor.Parse("#3B0764"), SKColor.Parse("#241D33"), SKColor.Parse("#111118"), SKColor.Parse("#A78BFA"), SKColor.Parse("#2E1065"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.MidnightBlue => new("Midnight Blue (PCB)", SKColor.Parse("#1D4ED8"), SKColor.Parse("#172554"), SKColor.Parse("#1E263D"), SKColor.Parse("#0E111B"), SKColor.Parse("#60A5FA"), SKColor.Parse("#1E3A8A"), SKColor.Parse("#EFF6FF"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, true, false, "GAME BOY"),
        GbcConsoleSkin.ClearIce => new("Clear Ice (PCB)", SKColor.Parse("#D8E4F0"), SKColor.Parse("#9AB0C7"), SKColor.Parse("#3B4354"), SKColor.Parse("#14171F"), SKColor.Parse("#BAE6FD"), SKColor.Parse("#64748B"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, true, false, "GAME BOY"),
        GbcConsoleSkin.ClearGreen => new("Clear Green Japan", SKColor.Parse("#22C55E"), SKColor.Parse("#14532D"), SKColor.Parse("#1B2B22"), SKColor.Parse("#0F1412"), SKColor.Parse("#86EFAC"), SKColor.Parse("#166534"), SKColor.Parse("#F0FDF4"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, true, false, "GAME BOY"),
        GbcConsoleSkin.PokemonPikachu => new("Pokemon Pikachu Edition", SKColor.Parse("#FDE047"), SKColor.Parse("#EAB308"), SKColor.Parse("#2563EB"), SKColor.Parse("#141824"), SKColor.Parse("#EF4444"), SKColor.Parse("#1D4ED8"), SKColor.Parse("#FEF08A"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
        GbcConsoleSkin.PokemonGoldSilver => new("Pokemon Gold & Silver", SKColor.Parse("#E8D5A7"), SKColor.Parse("#A69062"), SKColor.Parse("#524B3B"), SKColor.Parse("#171512"), SKColor.Parse("#FDE68A"), SKColor.Parse("#78716C"), SKColor.Parse("#FFFBEB"), SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, true, "GAME BOY"),
        GbcConsoleSkin.StormAuroraGbc => new("STORM Aurora GBC", SKColor.Parse("#130B29"), SKColor.Parse("#070414"), SKColor.Parse("#A855F7"), SKColor.Parse("#090612"), SKColor.Parse("#A855F7"), SKColor.Parse("#00E5FF"), SKColor.Parse("#F3E8FF"), SKColor.Parse("#A855F7"), HardwareShellForm.Aurora, false, false, "STORM GBC AURORA"),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#11131A"), SKColor.Parse("#7B42F6"), SKColor.Parse("#7B42F6"), SKColors.White, SKColor.Parse("#FF2442"), HardwareShellForm.Gbc, false, false, "GAME BOY"),
    };

    public static ConsoleSkinPalette GetGbaPalette(GbaConsoleSkin skin) => skin switch
    {
        GbaConsoleSkin.GbaIndigo => new("AGB-001 Indigo", SKColor.Parse("#5A52C8"), SKColor.Parse("#342E8A"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#1A1D26"), SKColor.Parse("#94A3B8"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, false, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaGlacier => new("AGB-001 Glacier (PCB)", SKColor.Parse("#B5D2E3"), SKColor.Parse("#749AB2"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#1E2430"), SKColor.Parse("#E0F2FE"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, true, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaArcticWhite => new("AGB-001 Arctic White", SKColor.Parse("#F4F7FA"), SKColor.Parse("#D1DAE5"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#242833"), SKColor.Parse("#94A3B8"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, false, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaSpiceOrange => new("AGB-001 Spice Orange", SKColor.Parse("#FB7A1A"), SKColor.Parse("#C2410C"), SKColor.Parse("#E5E7EB"), SKColor.Parse("#1C1D24"), SKColor.Parse("#FED7AA"), SKColor.Parse("#CBD5E1"), SKColor.Parse("#FFF7ED"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, false, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaFuchsia => new("AGB-001 Fuchsia (PCB)", SKColor.Parse("#EC4899"), SKColor.Parse("#9D174D"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#1F1926"), SKColor.Parse("#FBCFE8"), SKColor.Parse("#E2E8F0"), SKColor.Parse("#FDF2F8"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, true, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaMidnightBlue => new("AGB-001 Midnight Blue", SKColor.Parse("#1E40AF"), SKColor.Parse("#172554"), SKColor.Parse("#94A3B8"), SKColor.Parse("#101524"), SKColor.Parse("#60A5FA"), SKColor.Parse("#64748B"), SKColor.Parse("#DBEAFE"), SKColor.Parse("#22C55E"), HardwareShellForm.Agb, true, false, "GAME BOY ADVANCE"),
        GbaConsoleSkin.GbaSpCobalt => new("GBA SP Cobalt Blue", SKColor.Parse("#2563EB"), SKColor.Parse("#162C75"), SKColor.Parse("#1E293B"), SKColor.Parse("#111520"), SKColor.Parse("#60A5FA"), SKColor.Parse("#0F172A"), SKColor.Parse("#DBEAFE"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpOnyx => new("GBA SP Onyx Black", SKColor.Parse("#2A2B31"), SKColor.Parse("#111216"), SKColor.Parse("#3F3F46"), SKColor.Parse("#0C0D10"), SKColor.Parse("#A1A1AA"), SKColor.Parse("#18181B"), SKColor.Parse("#F4F4F5"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpPlatinum => new("GBA SP Platinum Silver", SKColor.Parse("#E4E7EC"), SKColor.Parse("#A5AEBC"), SKColor.Parse("#64748B"), SKColor.Parse("#1C2029"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#334155"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpFlameRed => new("GBA SP Flame Red", SKColor.Parse("#DC2626"), SKColor.Parse("#7F1D1D"), SKColor.Parse("#27272A"), SKColor.Parse("#131114"), SKColor.Parse("#FCA5A5"), SKColor.Parse("#450A0A"), SKColor.Parse("#FEF2F2"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpPearlBlue => new("GBA SP Pearl Blue AGS-101", SKColor.Parse("#93C5FD"), SKColor.Parse("#3B82F6"), SKColor.Parse("#334155"), SKColor.Parse("#141B29"), SKColor.Parse("#DBEAFE"), SKColor.Parse("#1D4ED8"), SKColor.Parse("#EFF6FF"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpNesClassic => new("GBA SP NES Classic", SKColor.Parse("#D5D4CE"), SKColor.Parse("#A7A6A0"), SKColor.Parse("#262626"), SKColor.Parse("#141414"), SKColor.Parse("#DC2626"), SKColor.Parse("#1F2937"), SKColor.Parse("#DC2626"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, false, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbaSpZeldaGold => new("GBA SP Zelda Triforce Gold", SKColor.Parse("#ECC94B"), SKColor.Parse("#9A6B12"), SKColor.Parse("#451A03"), SKColor.Parse("#14110B"), SKColor.Parse("#FEF08A"), SKColor.Parse("#78350F"), SKColor.Parse("#FEF08A"), SKColor.Parse("#22C55E"), HardwareShellForm.GbaSp, false, true, "GAME BOY ADVANCE SP"),
        GbaConsoleSkin.GbmFamicom => new("GB Micro Famicom 20th", SKColor.Parse("#991B1B"), SKColor.Parse("#560A0A"), SKColor.Parse("#F5D061"), SKColor.Parse("#1C1008"), SKColor.Parse("#FDE047"), SKColor.Parse("#D4AF37"), SKColor.Parse("#FEF08A"), SKColor.Parse("#38BDF8"), HardwareShellForm.GbMicro, false, true, "GAME BOY micro"),
        GbaConsoleSkin.GbmSilver => new("GB Micro Classic Silver", SKColor.Parse("#D4D8DF"), SKColor.Parse("#8C94A2"), SKColor.Parse("#27272A"), SKColor.Parse("#0D0E12"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#18181B"), SKColor.Parse("#F8FAFC"), SKColor.Parse("#38BDF8"), HardwareShellForm.GbMicro, false, true, "GAME BOY micro"),
        GbaConsoleSkin.StormAuroraGba => new("STORM Aurora GBA", SKColor.Parse("#0D1429"), SKColor.Parse("#050814"), SKColor.Parse("#00E5FF"), SKColor.Parse("#070B16"), SKColor.Parse("#00E5FF"), SKColor.Parse("#7C3AED"), SKColor.Parse("#E0F2FE"), SKColor.Parse("#00E5FF"), HardwareShellForm.Aurora, false, false, "STORM GBA AURORA"),
        _ => new("None", SKColors.Transparent, SKColors.Transparent, SKColor.Parse("#11131A"), SKColor.Parse("#11131A"), SKColor.Parse("#4F46E5"), SKColor.Parse("#4F46E5"), SKColors.White, SKColor.Parse("#22C55E"), HardwareShellForm.Agb, false, false, "GAME BOY ADVANCE"),
    };

    public static bool IsSkinNone(StormPlatform platform, GbcaAppSettings settings) => platform switch
    {
        StormPlatform.Gb or StormPlatform.Sgb => settings.GbConsoleSkin == GbConsoleSkin.None,
        StormPlatform.Gbc => settings.GbcConsoleSkin == GbcConsoleSkin.None,
        _ => settings.GbaConsoleSkin == GbaConsoleSkin.None,
    };

    public static bool ShouldHideSkinForScreen(StormPlatform platform, GbcaAppSettings settings)
    {
        if (IsSkinNone(platform, settings)) return true;
        var sr = settings.GetOverlayProfileForPlatform(platform).ScreenRect;
        if (sr == null) return false;
        if (sr.ScreenPreset == 2) return true;
        if (!sr.HideSkinInFullscreen) return false;
        if (StormGbcaUiState.Instance.IsFullscreen) return true;
        if (sr.UseCustom && (sr.WidthFraction >= 0.94f || sr.HeightFraction >= 0.92f)) return true;
        return false;
    }

    public static ConsoleSkinPalette ResolvePalette(StormPlatform platform, GbcaAppSettings settings) => platform switch
    {
        StormPlatform.Gb or StormPlatform.Sgb => GetGbPalette(settings.GbConsoleSkin),
        StormPlatform.Gbc => GetGbcPalette(settings.GbcConsoleSkin),
        _ => GetGbaPalette(settings.GbaConsoleSkin),
    };

    /// <summary>
    /// Computes exact LCD viewport rectangle inside container (width x height), honoring all 5 ScreenPresets:
    /// 0 = Inside 3D Skin, 1 = Max Aspect Fit, 2 = Fullscreen 100% Stretch, 3 = Integer Pixel-Perfect, 4 = Free Custom.
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

        int preset = custom?.ScreenPreset ?? 0;

        // 1. Free Custom screen geometry
        if (custom != null && (custom.UseCustom || preset == 4))
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

        // 2. Fullscreen 100% edge-to-edge fill
        if (preset == 2)
        {
            return new Rect(0, 0, Math.Round(width), Math.Round(height));
        }

        // 3. Max Aspect Fit (edge-to-edge preserving aspect ratio)
        if (preset == 1)
        {
            double mW, mH;
            if (width / height > aspect)
            {
                mH = height;
                mW = mH * aspect;
            }
            else
            {
                mW = width;
                mH = mW / aspect;
            }
            return new Rect(Math.Round((width - mW) * 0.5), Math.Round((height - mH) * 0.5), Math.Round(mW), Math.Round(mH));
        }

        // 4. Integer Pixel-Perfect centered
        if (preset == 3)
        {
            int k = Math.Max(1, (int)Math.Floor(Math.Min(width / srcW, height / srcH)));
            double pW = Math.Min(width, srcW * k);
            double pH = Math.Min(height, srcH * k);
            return new Rect(Math.Round((width - pW) * 0.5), Math.Round((height - pH) * 0.5), Math.Round(pW), Math.Round(pH));
        }

        // 5. Default Inside 3D Skin (or No Skin)
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

        bool isWide = palette.Form is HardwareShellForm.Agb or HardwareShellForm.GbMicro;
        double padX = isWide ? width * 0.16 : width * 0.21;
        double padY = height * 0.16;
        var screenRect = new Rect(padX, padY, width - padX * 2, height - padY * 2.35);
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
                using var nonePaint = new SKPaint { Color = SKColor.Parse("#334155"), Style = SKPaintStyle.Stroke, StrokeWidth = 2, IsAntialias = true };
                canvas.DrawRoundRect(new SKRoundRect(new SKRect(12, 12, w - 12, h - 12), 10), nonePaint);
                using var txtFont = new SKFont(SKTypeface.Default, 11.5f);
                using var txtPaint = new SKPaint { Color = SKColor.Parse("#E2E8F0"), IsAntialias = true };
                canvas.DrawText("БЕЗ КОРПУСА", w * 0.5f, h * 0.54f, SKTextAlign.Center, txtFont, txtPaint);
            }
            return;
        }

        float sx = (float)screenRect.X;
        float sy = (float)screenRect.Y;
        float sw = (float)screenRect.Width;
        float sh = (float)screenRect.Height;

        float margin = isPreview ? 6f : 12f;
        float bezelPadX = isPreview ? 11f : Math.Clamp(sw * 0.085f, 18f, 52f);
        float bezelPadTop = isPreview ? 9f : Math.Clamp(sh * 0.075f, 15f, 36f);
        float bezelPadBottom = isPreview ? 13f : Math.Clamp(sh * 0.11f, 22f, 44f);

        float extraSide = p.Form switch
        {
            HardwareShellForm.Agb => isPreview ? 14f : 42f,
            HardwareShellForm.GbMicro => isPreview ? 12f : 34f,
            _ => isPreview ? 7f : 24f,
        };
        float shellLeft = Math.Max(margin, sx - bezelPadX - extraSide);
        float shellTop = Math.Max(margin, sy - bezelPadTop - (isPreview ? 5f : 16f));
        float shellRight = Math.Min(w - margin, sx + sw + bezelPadX + extraSide);
        float shellBottom = Math.Min(h - margin, sy + sh + bezelPadBottom + (isPreview ? 16f : 28f));

        float rTL = p.Form == HardwareShellForm.GbaSp ? 16f : (p.Form == HardwareShellForm.GbMicro ? 22f : (p.Form == HardwareShellForm.Agb ? 28f : 18f));
        float rTR = rTL;
        float rBL = p.Form == HardwareShellForm.Dmg ? 14f : rTL;
        float rBR = p.Form == HardwareShellForm.Dmg ? (isPreview ? 22f : 42f) : rTL;

        var shellRect = new SKRect(shellLeft, shellTop, shellRight, shellBottom);
        var shellRr = new SKRoundRect();
        shellRr.SetRectRadii(shellRect, [new SKPoint(rTL, rTL), new SKPoint(rTR, rTR), new SKPoint(rBR, rBR), new SKPoint(rBL, rBL)]);

        // 1. 3D Drop Shadow
        using (var shadowPaint = new SKPaint
        {
            Color = new SKColor(0, 0, 0, 155),
            MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, isPreview ? 4f : 14f),
            IsAntialias = true
        })
        {
            var shRect = shellRect;
            shRect.Offset(0, isPreview ? 2.5f : 8f);
            var shRr = new SKRoundRect();
            shRr.SetRectRadii(shRect, [new SKPoint(rTL, rTL), new SKPoint(rTR, rTR), new SKPoint(rBR, rBR), new SKPoint(rBL, rBL)]);
            canvas.DrawRoundRect(shRr, shadowPaint);
        }

        // 1b. AGB-001 sculpted L/R shoulder bumpers
        if (p.Form == HardwareShellForm.Agb)
        {
            float shoulderW = (shellRight - shellLeft) * 0.22f;
            float shoulderH = isPreview ? 7f : 15f;
            using var shoulderPaint = new SKPaint { Color = p.SecondaryTrim, IsAntialias = true };
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellLeft + 8f, shellTop - shoulderH * 0.35f, shellLeft + 8f + shoulderW, shellTop + shoulderH), 8f), shoulderPaint);
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellRight - 8f - shoulderW, shellTop - shoulderH * 0.35f, shellRight - 8f, shellTop + shoulderH), 8f), shoulderPaint);
        }

        // 2. Main 3D Metallic / Molded Plastic Shell Body
        using (var bodyPaint = new SKPaint
        {
            IsAntialias = true,
            Shader = SKShader.CreateLinearGradient(
                new SKPoint(shellLeft, shellTop),
                new SKPoint(shellRight, shellBottom),
                p.IsMetallic ? new SKColor[] { p.BodyTop, MixColor(p.BodyTop, SKColors.White, 0.22f), p.BodyBottom } : new SKColor[] { p.BodyTop, p.BodyBottom },
                (float[]?)null,
                SKShaderTileMode.Clamp)
        })
        {
            canvas.DrawRoundRect(shellRr, bodyPaint);
        }

        // 3. Translucent shell internal green/gold PCB, QFP CPU chip, traces & corner screw posts
        if (p.IsTranslucent)
        {
            canvas.Save();
            canvas.ClipRoundRect(shellRr, SKClipOperation.Intersect, true);
            using var pcbFill = new SKPaint { Color = new SKColor(22, 92, 62, 68), IsAntialias = true };
            canvas.DrawRect(new SKRect(shellLeft + 6, shellTop + 6, shellRight - 6, shellBottom - 6), pcbFill);

            using var tracePaint = new SKPaint { Color = new SKColor(235, 198, 92, 85), Style = SKPaintStyle.Stroke, StrokeWidth = isPreview ? 0.9f : 1.5f, IsAntialias = true };
            float step = isPreview ? 12f : 24f;
            for (float tx = shellLeft + 10; tx < shellRight - 10; tx += step)
            {
                canvas.DrawLine(tx, shellTop + 5, tx + 6, shellTop + (isPreview ? 14 : 26), tracePaint);
                canvas.DrawCircle(tx + 6, shellTop + (isPreview ? 14 : 26), isPreview ? 1.2f : 2.3f, tracePaint);
                canvas.DrawLine(tx, shellBottom - 5, tx - 5, shellBottom - (isPreview ? 12 : 22), tracePaint);
            }
            // Internal QFP CPU chip silhouette on bottom left
            using var chipPaint = new SKPaint { Color = new SKColor(15, 18, 24, 95), IsAntialias = true };
            float chipSz = isPreview ? 11f : 24f;
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellLeft + 10, shellBottom - chipSz - 6, shellLeft + 10 + chipSz, shellBottom - 6), 2f), chipPaint);

            // Corner screw posts
            using var postPaint = new SKPaint { Color = new SKColor(255, 255, 255, 72), Style = SKPaintStyle.Stroke, StrokeWidth = isPreview ? 1.1f : 2f, IsAntialias = true };
            float pr = isPreview ? 2.5f : 5f;
            canvas.DrawCircle(shellLeft + 10, shellTop + 10, pr, postPaint);
            canvas.DrawCircle(shellRight - 10, shellTop + 10, pr, postPaint);
            canvas.DrawCircle(shellLeft + 10, shellBottom - 10, pr, postPaint);
            canvas.DrawCircle(shellRight - 10, shellBottom - 10, pr, postPaint);
            canvas.Restore();
        }

        // 4. Form-Factor Authentic Hardware Details on Shell
        if (p.Form == HardwareShellForm.Dmg)
        {
            // Iconic DMG top recessed horizontal channel groove + vertical notches
            float grooveY = shellTop + (isPreview ? 4.5f : 10f);
            using var groovePaint = new SKPaint { Color = new SKColor(0, 0, 0, 58), StrokeWidth = isPreview ? 1.2f : 2.2f, IsAntialias = true };
            canvas.DrawLine(shellLeft + 4, grooveY, shellRight - 4, grooveY, groovePaint);
            canvas.DrawLine(shellLeft + (isPreview ? 14 : 28), shellTop + 1, shellLeft + (isPreview ? 14 : 28), grooveY, groovePaint);
            canvas.DrawLine(shellRight - (isPreview ? 14 : 28), shellTop + 1, shellRight - (isPreview ? 14 : 28), grooveY, groovePaint);
        }
        else if (p.Form == HardwareShellForm.Gbc)
        {
            // Dark IR port window at top edge
            float irW = isPreview ? 16f : 36f;
            float irH = isPreview ? 3.5f : 7f;
            using var irPaint = new SKPaint { Color = SKColor.Parse("#1A0B14"), IsAntialias = true };
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellRect.MidX - irW * 0.5f, shellTop + 1.5f, shellRect.MidX + irW * 0.5f, shellTop + 1.5f + irH), 3f), irPaint);
        }
        else if (p.Form == HardwareShellForm.Sgb)
        {
            // SNES lavender ornamental side columns
            using var colPaint = new SKPaint { Color = p.BezelOuter.WithAlpha(90), IsAntialias = true };
            float cw = isPreview ? 6f : 14f;
            canvas.DrawRect(new SKRect(shellLeft + 4, shellTop + 12, shellLeft + 4 + cw, shellBottom - 12), colPaint);
            canvas.DrawRect(new SKRect(shellRight - 4 - cw, shellTop + 12, shellRight - 4, shellBottom - 12), colPaint);
        }
        else if (p.Form == HardwareShellForm.GbMicro)
        {
            // Detachable brushed metallic faceplate frame
            var fpRect = shellRect;
            fpRect.Inflate(isPreview ? -3.5f : -8f, isPreview ? -3.5f : -8f);
            using var fpPaint = new SKPaint { Color = p.BezelOuter, Style = SKPaintStyle.Stroke, StrokeWidth = isPreview ? 2.2f : 4.5f, IsAntialias = true };
            canvas.DrawRoundRect(new SKRoundRect(fpRect, 16f), fpPaint);
        }

        // 5. Outer 3D Bevel Rim
        using (var rimPaint = new SKPaint
        {
            Style = SKPaintStyle.Stroke,
            StrokeWidth = p.IsNeon ? 2.2f : 1.5f,
            IsAntialias = true,
            Color = p.IsNeon ? p.AccentColor : new SKColor(255, 255, 255, 115)
        })
        {
            var inset = shellRect;
            inset.Inflate(-1.5f, -1.5f);
            var inRr = new SKRoundRect();
            inRr.SetRectRadii(inset, [new SKPoint(rTL - 1, rTL - 1), new SKPoint(rTR - 1, rTR - 1), new SKPoint(rBR - 1, rBR - 1), new SKPoint(rBL - 1, rBL - 1)]);
            canvas.DrawRoundRect(inRr, rimPaint);
        }

        // 6. GBA SP 3-segment metallic clamshell hinge + brightness button + 16-hole diamond speaker
        if (p.Form == HardwareShellForm.GbaSp)
        {
            float hingeY = shellBottom - (isPreview ? 9f : 16f);
            float hingeH = isPreview ? 5.5f : 11f;
            using var hingePaint = new SKPaint
            {
                Shader = SKShader.CreateLinearGradient(
                    new SKPoint(shellLeft, hingeY - hingeH * 0.5f),
                    new SKPoint(shellLeft, hingeY + hingeH * 0.5f),
                    [new SKColor(255, 255, 255, 145), p.BodyBottom, new SKColor(0, 0, 0, 185)],
                    null,
                    SKShaderTileMode.Clamp),
                IsAntialias = true
            };
            canvas.DrawRoundRect(new SKRoundRect(new SKRect(shellLeft + 8, hingeY - hingeH * 0.5f, shellRight - 8, hingeY + hingeH * 0.5f), hingeH * 0.5f), hingePaint);
            // Two hinge barrel seam lines (3-segment clamshell)
            using var seamPaint = new SKPaint { Color = new SKColor(0, 0, 0, 140), StrokeWidth = isPreview ? 1f : 2f, IsAntialias = true };
            float s1 = shellLeft + (shellRight - shellLeft) * 0.24f;
            float s2 = shellLeft + (shellRight - shellLeft) * 0.76f;
            canvas.DrawLine(s1, hingeY - hingeH * 0.5f, s1, hingeY + hingeH * 0.5f, seamPaint);
            canvas.DrawLine(s2, hingeY - hingeH * 0.5f, s2, hingeY + hingeH * 0.5f, seamPaint);

            // Zelda Triforce gold emblem on Zelda edition
            if (p.DisplayName.Contains("Zelda", StringComparison.OrdinalIgnoreCase))
            {
                using var triPaint = new SKPaint { Color = SKColor.Parse("#FEF08A"), IsAntialias = true };
                float tx = shellRight - (isPreview ? 14f : 28f);
                float ty = shellTop + (isPreview ? 10f : 18f);
                float ts = isPreview ? 3.5f : 6.5f;
                DrawTriangle(canvas, tx, ty - ts, ts, triPaint);
                DrawTriangle(canvas, tx - ts * 0.6f, ty + ts * 0.1f, ts, triPaint);
                DrawTriangle(canvas, tx + ts * 0.6f, ty + ts * 0.1f, ts, triPaint);
            }
        }

        // 7. Recessed Screen Lens Bezel
        var bezelRect = new SKRect(sx - bezelPadX, sy - bezelPadTop, sx + sw + bezelPadX, sy + sh + bezelPadBottom);
        float bezelBr = p.Form == HardwareShellForm.Dmg ? (isPreview ? 16f : 28f) : 12f;
        var bezelRr = new SKRoundRect();
        bezelRr.SetRectRadii(bezelRect, [new SKPoint(12f, 12f), new SKPoint(12f, 12f), new SKPoint(bezelBr, bezelBr), new SKPoint(12f, 12f)]);

        using (var bezelPaint = new SKPaint { Color = p.BezelInner, IsAntialias = true })
        {
            canvas.DrawRoundRect(bezelRr, bezelPaint);
        }
        using (var bezelRim = new SKPaint
        {
            Color = p.Form == HardwareShellForm.Light ? SKColor.Parse("#2DD4BF") : (p.IsNeon ? p.AccentColor : p.BezelOuter),
            Style = SKPaintStyle.Stroke,
            StrokeWidth = p.Form is HardwareShellForm.Pocket or HardwareShellForm.Light or HardwareShellForm.Aurora ? 2.2f : 1.5f,
            IsAntialias = true
        })
        {
            canvas.DrawRoundRect(bezelRr, bezelRim);
        }

        // DMG iconic twin magenta + navy stripes across top of bezel
        if (p.Form == HardwareShellForm.Dmg)
        {
            float stY1 = bezelRect.Top + (isPreview ? 3.2f : 7f);
            float stY2 = stY1 + (isPreview ? 2.2f : 4.2f);
            using var sMag = new SKPaint { Color = p.AccentColor, StrokeWidth = isPreview ? 1.3f : 2.3f, IsAntialias = true };
            using var sNav = new SKPaint { Color = p.SecondaryTrim, StrokeWidth = isPreview ? 1.3f : 2.3f, IsAntialias = true };
            canvas.DrawLine(bezelRect.Left + 8, stY1, bezelRect.Right - 8, stY1, sMag);
            canvas.DrawLine(bezelRect.Left + 8, stY2, bezelRect.Right - 8, stY2, sNav);
        }
        // GBA SP 4 corner rubber screen bumpers
        else if (p.Form == HardwareShellForm.GbaSp)
        {
            using var bumpPaint = new SKPaint { Color = new SKColor(28, 30, 36, 220), IsAntialias = true };
            float br = isPreview ? 1.8f : 3.6f;
            canvas.DrawCircle(bezelRect.Left + 6, bezelRect.Top + 6, br, bumpPaint);
            canvas.DrawCircle(bezelRect.Right - 6, bezelRect.Top + 6, br, bumpPaint);
            canvas.DrawCircle(bezelRect.Left + 6, bezelRect.Bottom - 6, br, bumpPaint);
            canvas.DrawCircle(bezelRect.Right - 6, bezelRect.Bottom - 6, br, bumpPaint);
        }

        // 8. Power LED (dual Power + Charge LEDs on GBA SP)
        float ledX = p.Form == HardwareShellForm.GbaSp ? bezelRect.Right - (isPreview ? 7f : 15f) : bezelRect.Left + (isPreview ? 5.5f : 13f);
        float ledY = bezelRect.Top + bezelRect.Height * 0.34f;
        SKColor ledCol = nightDimming ? p.PowerLedColor.WithAlpha(110) : p.PowerLedColor;

        using (var glowPaint = new SKPaint
        {
            Color = ledCol.WithAlpha(nightDimming ? (byte)55 : (byte)165),
            MaskFilter = SKMaskFilter.CreateBlur(SKBlurStyle.Normal, isPreview ? 2.5f : 6.5f),
            IsAntialias = true
        })
        {
            canvas.DrawCircle(ledX, ledY, isPreview ? 3.2f : 6.5f, glowPaint);
        }
        using (var ledCore = new SKPaint { Color = ledCol, IsAntialias = true })
        {
            canvas.DrawCircle(ledX, ledY, isPreview ? 1.6f : 3.1f, ledCore);
        }
        if (p.Form == HardwareShellForm.GbaSp)
        {
            using var chgCore = new SKPaint { Color = SKColor.Parse("#F59E0B").WithAlpha(180), IsAntialias = true };
            canvas.DrawCircle(ledX, ledY + (isPreview ? 5.5f : 11f), isPreview ? 1.3f : 2.5f, chgCore);
        }

        // 9. Authentic Branding inside bottom bezel (including 5-color GBC "COLOR")
        float brandSize = isPreview ? 6.8f : Math.Clamp(bezelPadBottom * 0.44f, 11f, 16f);
        float brandY = bezelRect.Bottom - (isPreview ? 3.5f : bezelPadBottom * 0.28f);
        using (var brandTypeface = SKTypeface.FromFamilyName("Segoe UI", SKFontStyleWeight.Bold, SKFontStyleWidth.Normal, SKFontStyleSlant.Italic))
        using (var brandFont = new SKFont(brandTypeface, brandSize))
        using (var brandPaint = new SKPaint { Color = p.TextColor, IsAntialias = true })
        {
            if (p.Form == HardwareShellForm.Gbc)
            {
                float gbW = brandFont.MeasureText("GAME BOY ");
                float startX = bezelRect.MidX - (gbW + brandSize * 3.1f) * 0.5f;
                canvas.DrawText("GAME BOY ", startX, brandY, SKTextAlign.Left, brandFont, brandPaint);
                string[] letters = ["C", "O", "L", "O", "R"];
                SKColor[] cols = [SKColor.Parse("#EF4444"), SKColor.Parse("#A855F7"), SKColor.Parse("#84CC16"), SKColor.Parse("#FACC15"), SKColor.Parse("#06B6D4")];
                float cx = startX + gbW;
                for (int i = 0; i < letters.Length; i++)
                {
                    brandPaint.Color = cols[i];
                    canvas.DrawText(letters[i], cx, brandY, SKTextAlign.Left, brandFont, brandPaint);
                    cx += brandFont.MeasureText(letters[i]);
                }
            }
            else
            {
                canvas.DrawText(p.BrandTitle, bezelRect.MidX, brandY, SKTextAlign.Center, brandFont, brandPaint);
            }
        }

        // 10. Recessed LCD Cutout / Preview Screen Mockup
        var lcdRect = new SKRect(sx, sy, sx + sw, sy + sh);
        if (isPreview)
        {
            using var lcdFill = new SKPaint
            {
                Shader = SKShader.CreateLinearGradient(
                    new SKPoint(lcdRect.Left, lcdRect.Top),
                    new SKPoint(lcdRect.Right, lcdRect.Bottom),
                    [SKColor.Parse("#1E3A5F"), SKColor.Parse("#0F172A")],
                    null,
                    SKShaderTileMode.Clamp),
                IsAntialias = true
            };
            canvas.DrawRect(lcdRect, lcdFill);
            using var dotFont = new SKFont(SKTypeface.Default, 8.5f);
            using var dotPaint = new SKPaint { Color = p.AccentColor, IsAntialias = true };
            canvas.DrawText(platform.ToString().ToUpperInvariant(), lcdRect.MidX, lcdRect.MidY + 3f, SKTextAlign.Center, dotFont, dotPaint);
        }
        else
        {
            using var lcdCutout = new SKPaint { Color = SKColor.Parse("#05060A"), IsAntialias = false };
            canvas.DrawRect(lcdRect, lcdCutout);
        }

        // 11. Authentic Speaker Grilles (6 diagonal slits on DMG, dot-matrix on GBC, 16-hole diamond on GBA SP)
        if (!isPreview)
        {
            using var spPaint = new SKPaint { Color = new SKColor(0, 0, 0, 145), StrokeWidth = 2.8f, StrokeCap = SKStrokeCap.Round, IsAntialias = true };
            if (p.Form == HardwareShellForm.Gbc)
            {
                float gx = shellRight - 46f;
                float gy = shellBottom - 22f;
                for (int r = 0; r < 3; r++)
                    for (int c = 0; c < 5; c++)
                        canvas.DrawCircle(gx + c * 6.5f, gy + r * 5.5f, 1.5f, spPaint);
            }
            else if (p.Form == HardwareShellForm.GbaSp)
            {
                // 4x4 diamond 16-hole speaker grille
                float gx = shellRect.MidX;
                float gy = shellBottom - 36f;
                for (int r = 0; r < 4; r++)
                    for (int c = 0; c < 4; c++)
                        canvas.DrawCircle(gx + (c - 1.5f) * 5.2f, gy + (r - 1.5f) * 5.2f, 1.3f, spPaint);
            }
            else
            {
                float spX = shellRight - 54f;
                float spY = shellBottom - 22f;
                for (int i = 0; i < 6; i++)
                {
                    float ox = i * 7.2f;
                    canvas.DrawLine(spX + ox, spY, spX + ox + 5f, spY + 12f, spPaint);
                }
            }
        }

        // 12. Optional Night Dimming Veil
        if (nightDimming && !isPreview)
        {
            using var dimPaint = new SKPaint { Color = new SKColor(4, 6, 12, 105), IsAntialias = true };
            canvas.DrawRoundRect(shellRr, dimPaint);
        }
    }

    private static void DrawTriangle(SKCanvas canvas, float cx, float cy, float size, SKPaint paint)
    {
        using var path = new SKPath();
        path.MoveTo(cx, cy - size * 0.55f);
        path.LineTo(cx - size * 0.55f, cy + size * 0.45f);
        path.LineTo(cx + size * 0.55f, cy + size * 0.45f);
        path.Close();
        canvas.DrawPath(path, paint);
    }

    private static SKColor MixColor(SKColor a, SKColor b, float t) => new(
        (byte)(a.Red + (b.Red - a.Red) * t),
        (byte)(a.Green + (b.Green - a.Green) * t),
        (byte)(a.Blue + (b.Blue - a.Blue) * t),
        a.Alpha);
}

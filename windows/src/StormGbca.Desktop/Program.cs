// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Text;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Platform;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.UI;
using StormGbca.UI.Windows;

namespace StormGbca.Desktop;

internal static class Program
{
    [STAThread]
    public static int Main(string[] args)
    {
        if (args.Contains("--smoke-test", StringComparer.OrdinalIgnoreCase))
        {
            return RunSmokeTest();
        }

        using var singleInstance = new AuroraSingleInstanceService("GBCA");
        bool isPrimary = singleInstance.TryAcquireOrNotifyExisting(() =>
        {
            Dispatcher.UIThread.Post(() =>
            {
                if (Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop &&
                    desktop.MainWindow != null)
                {
                    var win = desktop.MainWindow;
                    if (!win.IsVisible) win.Show();
                    if (win.WindowState == WindowState.Minimized) win.WindowState = WindowState.Normal;
                    win.Activate();
                }
            });
        });

        if (!isPrimary)
        {
            return 0;
        }

        return BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    public static AppBuilder BuildAvaloniaApp()
    {
        var builder = AppBuilder.Configure<StormGbcaApp>()
            .UsePlatformDetect()
            .With(new Win32PlatformOptions
            {
                RenderingMode =
                [
                    Win32RenderingMode.Vulkan,
                    Win32RenderingMode.AngleEgl,
                    Win32RenderingMode.Wgl,
                    Win32RenderingMode.Software
                ],
                CompositionMode =
                [
                    Win32CompositionMode.WinUIComposition,
                    Win32CompositionMode.DirectComposition,
                    Win32CompositionMode.RedirectionSurface
                ]
            });

        return AuroraGpuBootstrap.ConfigureAuroraGpu(builder);
    }

    private static int RunSmokeTest()
    {
        Console.WriteLine($"=== STORM GBCA {GbcaLoc.AppVersion} SMOKE TEST ===");

        // 1. Initialize Avalonia framework & verify StormGbcaMainWindow instantiation
        var lifetime = new ClassicDesktopStyleApplicationLifetime();
        BuildAvaloniaApp().SetupWithLifetime(lifetime);
        AuroraIconCatalog.VerifyZeroDuplicates();
        int iconCount = Enum.GetValues<AuroraIconKind>().Length;
        int themeCount = AuroraThemeManager.Instance.GetAvailableThemes().Count;
        Console.WriteLine($"[OK] Avalonia initialized | Icons={iconCount} | Themes={themeCount}");

        var mainWin = lifetime.MainWindow as StormGbcaMainWindow ?? new StormGbcaMainWindow();
        bool windowCreated = !string.IsNullOrWhiteSpace(mainWin.Title);
        Console.WriteLine($"[OK] MainWindow instantiated: '{mainWin.Title}'");

        // 2. Native storm_gbca_core.dll P/Invoke verification + synthetic GB / GBC / GBA frames
        bool coreAvail = NativeCoreBindings.IsAvailable;
        string coreVer = coreAvail ? NativeCoreBindings.GetVersionString() : "MISSING";
        int palettes = coreAvail ? NativeCoreBindings.storm_gbca_get_dmg_palette_count() : 0;
        Console.WriteLine($"[OK] Native Core: Available={coreAvail}, Version={coreVer}, Palettes={palettes}");
        if (!coreAvail)
        {
            Console.Error.WriteLine("[FAIL] storm_gbca_core.dll failed to load.");
            return 2;
        }

        using var emu = new GbcaEmulatorSession();
        byte[] frameBuf = [];

        bool gbOk = TestSyntheticRom(emu, MakeSyntheticGbRom(false), "smoke_gb.gb", StormCoreBackend.SameBoy, 160, 144, ref frameBuf, out int gbW, out int gbH);
        bool gbcOk = TestSyntheticRom(emu, MakeSyntheticGbRom(true), "smoke_gbc.gbc", StormCoreBackend.SameBoy, 160, 144, ref frameBuf, out int gbcW, out int gbcH);
        bool gbaOk = TestSyntheticRom(emu, MakeSyntheticGbaRom(), "smoke_gba.gba", StormCoreBackend.Mgba, 240, 160, ref frameBuf, out int gbaW, out int gbaH);

        Console.WriteLine($"[OK] Synthetic Frame Tests: GB({gbW}x{gbH})={gbOk}, GBC({gbcW}x{gbcH})={gbcOk}, GBA({gbaW}x{gbaH})={gbaOk}");

        // 3. Write JSON smoke report
        StormGbcaPaths.EnsureDirectories();
        string reportPath = Path.Combine(StormGbcaPaths.LogsDirectory, "smoke_test_report.json");
        string jsonReport = $$"""
        {
          "app": "STORM GBCA",
          "version": "{{GbcaLoc.AppVersion}}",
          "avaloniaInitialized": true,
          "mainWindowTitle": "{{mainWin.Title}}",
          "nativeCoreAvailable": {{coreAvail.ToString().ToLowerInvariant()}},
          "nativeCoreVersion": "{{coreVer}}",
          "dmgPaletteCount": {{palettes}},
          "gbTest": { "passed": {{gbOk.ToString().ToLowerInvariant()}}, "width": {{gbW}}, "height": {{gbH}} },
          "gbcTest": { "passed": {{gbcOk.ToString().ToLowerInvariant()}}, "width": {{gbcW}}, "height": {{gbcH}} },
          "gbaTest": { "passed": {{gbaOk.ToString().ToLowerInvariant()}}, "width": {{gbaW}}, "height": {{gbaH}} },
          "timestampUtc": "{{DateTime.UtcNow:O}}"
        }
        """;
        File.WriteAllText(reportPath, jsonReport, Encoding.UTF8);
        Console.WriteLine($"[OK] Smoke Report written: {reportPath}");

        bool allPassed = windowCreated && coreAvail && gbOk && gbcOk && gbaOk;
        mainWin.Close();
        Console.WriteLine(allPassed ? "=== ALL SMOKE TESTS PASSED (EXIT 0) ===" : "=== SMOKE TEST FAILED ===");
        return allPassed ? 0 : 1;
    }

    private static bool TestSyntheticRom(
        GbcaEmulatorSession emu,
        byte[] rom,
        string virtualName,
        StormCoreBackend core,
        int expectedW,
        int expectedH,
        ref byte[] frameBuf,
        out int actualW,
        out int actualH)
    {
        actualW = 0;
        actualH = 0;
        if (!emu.LoadRomBufferForTest(rom, virtualName, core)) return false;
        for (int f = 0; f < 8; f++)
        {
            if (!emu.RunSingleFrameForTest(out actualW, out actualH)) return false;
        }
        int copied = emu.CopyCurrentFrameBgra(ref frameBuf, out actualW, out actualH);
        return copied > 0 && actualW == expectedW && actualH == expectedH;
    }

    private static byte[] MakeSyntheticGbRom(bool gbc)
    {
        byte[] rom = new byte[32768];
        rom[0x0100] = 0x00;
        rom[0x0101] = 0xC3;
        rom[0x0102] = 0x50;
        rom[0x0103] = 0x01;
        byte[] logo =
        [
            0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D,
            0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E, 0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99,
            0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC, 0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E
        ];
        Array.Copy(logo, 0, rom, 0x0104, logo.Length);
        byte[] titleBytes = Encoding.ASCII.GetBytes(gbc ? "STORMGBCTEST" : "STORMGBTEST");
        Array.Copy(titleBytes, 0, rom, 0x0134, titleBytes.Length);
        if (gbc) rom[0x0143] = 0x80;

        byte chk = 0;
        for (int i = 0x0134; i <= 0x014C; i++) chk = (byte)(chk - rom[i] - 1);
        rom[0x014D] = chk;

        // JR -2 infinite loop at 0x0150
        rom[0x0150] = 0x18;
        rom[0x0151] = 0xFE;
        return rom;
    }

    private static byte[] MakeSyntheticGbaRom()
    {
        byte[] rom = new byte[65536];
        rom[0] = 0x2E; rom[1] = 0x00; rom[2] = 0x00; rom[3] = 0xEA;
        rom[0xB2] = 0x96;
        Encoding.ASCII.GetBytes("STORMGBATST").CopyTo(rom, 0xA0);
        Encoding.ASCII.GetBytes("STME").CopyTo(rom, 0xAC);
        rom[0xB0] = (byte)'0';
        rom[0xB1] = (byte)'1';
        byte sum = 0;
        for (int i = 0xA0; i <= 0xBC; i++) sum = (byte)(sum + rom[i]);
        rom[0xBD] = (byte)-(sum + 0x19);
        // EAFFFFFE -> B . at 0xC0
        rom[0xC0] = 0xFE; rom[0xC1] = 0xFF; rom[0xC2] = 0xFF; rom[0xC3] = 0xEA;
        return rom;
    }
}

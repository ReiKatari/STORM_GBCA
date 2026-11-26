// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text.Json;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Subsystems;

/// <summary>
/// Manages per-ROM GameShark, ActionReplay, and GameGenie cheats and pushes them into storm_gbca_core.dll.
/// </summary>
public sealed class GbcaCheatService
{
    private readonly List<GbcaCheatEntry> _cheats = [];
    private readonly object _lock = new();

    public IReadOnlyList<GbcaCheatEntry> Cheats
    {
        get
        {
            lock (_lock) return _cheats.ToList();
        }
    }

    public void LoadCheatsForRom(string romKey)
    {
        if (string.IsNullOrWhiteSpace(romKey)) return;
        string path = StormGbcaPaths.GetCheatsFilePath(romKey);
        lock (_lock)
        {
            _cheats.Clear();
            if (File.Exists(path))
            {
                try
                {
                    string json = File.ReadAllText(path);
                    var items = JsonSerializer.Deserialize(json, GbcaJsonContext.Default.ListGbcaCheatEntry);
                    if (items != null) _cheats.AddRange(items);
                }
                catch
                {
                }
            }
        }
    }

    public void SaveCheatsForRom(string romKey)
    {
        if (string.IsNullOrWhiteSpace(romKey)) return;
        try
        {
            Directory.CreateDirectory(StormGbcaPaths.CheatsDirectory);
            string path = StormGbcaPaths.GetCheatsFilePath(romKey);
            lock (_lock)
            {
                string json = JsonSerializer.Serialize(_cheats, GbcaJsonContext.Default.ListGbcaCheatEntry);
                File.WriteAllText(path, json);
            }
        }
        catch
        {
        }
    }

    public void AddCheat(GbcaCheatEntry entry, string romKey)
    {
        lock (_lock) _cheats.Add(entry);
        SaveCheatsForRom(romKey);
    }

    public void ToggleCheat(int index, string romKey)
    {
        lock (_lock)
        {
            if (index >= 0 && index < _cheats.Count)
            {
                _cheats[index].Enabled = !_cheats[index].Enabled;
            }
        }
        SaveCheatsForRom(romKey);
    }

    public void RemoveCheat(int index, string romKey)
    {
        lock (_lock)
        {
            if (index >= 0 && index < _cheats.Count)
            {
                _cheats.RemoveAt(index);
            }
        }
        SaveCheatsForRom(romKey);
    }

    public void ApplyToNative(nint nativeCtx, bool cheatsAllowed = true)
    {
        if (nativeCtx == nint.Zero || !NativeCoreBindings.IsAvailable) return;

        NativeCoreBindings.storm_gbca_cheats_clear(nativeCtx);
        if (!cheatsAllowed) return;

        List<GbcaCheatEntry> snapshot;
        lock (_lock) snapshot = _cheats.Where(c => c.Enabled).ToList();

        foreach (var cheat in snapshot)
        {
            if (!string.IsNullOrWhiteSpace(cheat.Code))
            {
                NativeCoreBindings.storm_gbca_cheat_add(
                    nativeCtx,
                    string.IsNullOrWhiteSpace(cheat.Name) ? "Cheat" : cheat.Name,
                    cheat.Code,
                    1);
            }
        }
    }
}

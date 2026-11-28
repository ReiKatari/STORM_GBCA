// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Runtime.InteropServices;
using StormGbca.Core.Native;

namespace StormGbca.Core.Input;

/// <summary>
/// NativeAOT-compatible XInput gamepad reader and force-feedback driver mapping Xbox / XInput
/// controller 0 to STORM GBCA button masks and cartridge rumble motors.
/// </summary>
public sealed unsafe class XInputGamepadService
{
    private const int Deadzone = 8000;

    private const ushort XINPUT_GAMEPAD_DPAD_UP = 0x0001;
    private const ushort XINPUT_GAMEPAD_DPAD_DOWN = 0x0002;
    private const ushort XINPUT_GAMEPAD_DPAD_LEFT = 0x0004;
    private const ushort XINPUT_GAMEPAD_DPAD_RIGHT = 0x0008;
    private const ushort XINPUT_GAMEPAD_START = 0x0010;
    private const ushort XINPUT_GAMEPAD_BACK = 0x0020;
    private const ushort XINPUT_GAMEPAD_LEFT_SHOULDER = 0x0100;
    private const ushort XINPUT_GAMEPAD_RIGHT_SHOULDER = 0x0200;
    private const ushort XINPUT_GAMEPAD_A = 0x1000;
    private const ushort XINPUT_GAMEPAD_B = 0x2000;
    private const ushort XINPUT_GAMEPAD_X = 0x4000;
    private const ushort XINPUT_GAMEPAD_Y = 0x8000;

    [StructLayout(LayoutKind.Sequential)]
    private struct XINPUT_GAMEPAD
    {
        public ushort wButtons;
        public byte bLeftTrigger;
        public byte bRightTrigger;
        public short sThumbLX;
        public short sThumbLY;
        public short sThumbRX;
        public short sThumbRY;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XINPUT_STATE
    {
        public uint dwPacketNumber;
        public XINPUT_GAMEPAD Gamepad;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XINPUT_VIBRATION
    {
        public ushort wLeftMotorSpeed;
        public ushort wRightMotorSpeed;
    }

    private readonly delegate* unmanaged[Stdcall]<uint, XINPUT_STATE*, uint> _xInputGetState;
    private readonly delegate* unmanaged[Stdcall]<uint, XINPUT_VIBRATION*, uint> _xInputSetState;
    private ushort _lastMotor;

    public bool IsSupported => _xInputGetState != null;
    public bool IsConnected { get; private set; }

    public XInputGamepadService()
    {
        if (!OperatingSystem.IsWindows()) return;

        nint lib = nint.Zero;
        if (!NativeLibrary.TryLoad("xinput1_4.dll", out lib) &&
            !NativeLibrary.TryLoad("xinput9_1_0.dll", out lib) &&
            !NativeLibrary.TryLoad("xinput1_3.dll", out lib))
        {
            return;
        }

        if (NativeLibrary.TryGetExport(lib, "XInputGetState", out nint getProc))
        {
            _xInputGetState = (delegate* unmanaged[Stdcall]<uint, XINPUT_STATE*, uint>)getProc;
        }

        if (NativeLibrary.TryGetExport(lib, "XInputSetState", out nint setProc))
        {
            _xInputSetState = (delegate* unmanaged[Stdcall]<uint, XINPUT_VIBRATION*, uint>)setProc;
        }
    }

    /// <summary>
    /// Reads XInput player 0 state and returns active-high STORM_KEY_* button bits.
    /// </summary>
    public uint PollButtonMask(out float thumbX, out float thumbY)
    {
        thumbX = 0f;
        thumbY = 0f;
        if (_xInputGetState == null)
        {
            IsConnected = false;
            return 0;
        }

        XINPUT_STATE state = default;
        if (_xInputGetState(0, &state) != 0)
        {
            IsConnected = false;
            return 0;
        }

        IsConnected = true;
        thumbX = Math.Clamp(state.Gamepad.sThumbLX / 32767f, -1f, 1f);
        thumbY = Math.Clamp(state.Gamepad.sThumbLY / 32767f, -1f, 1f);

        ushort b = state.Gamepad.wButtons;
        uint mask = 0;

        // Nintendo layout: Xbox B -> A, Xbox A -> B, Xbox X -> A (alt), Xbox Y -> B (alt)
        if ((b & XINPUT_GAMEPAD_B) != 0 || (b & XINPUT_GAMEPAD_X) != 0) mask |= (uint)StormKeyMask.A;
        if ((b & XINPUT_GAMEPAD_A) != 0 || (b & XINPUT_GAMEPAD_Y) != 0) mask |= (uint)StormKeyMask.B;
        if ((b & XINPUT_GAMEPAD_BACK) != 0) mask |= (uint)StormKeyMask.Select;
        if ((b & XINPUT_GAMEPAD_START) != 0) mask |= (uint)StormKeyMask.Start;

        if ((b & XINPUT_GAMEPAD_DPAD_RIGHT) != 0 || state.Gamepad.sThumbLX > Deadzone) mask |= (uint)StormKeyMask.Right;
        if ((b & XINPUT_GAMEPAD_DPAD_LEFT) != 0 || state.Gamepad.sThumbLX < -Deadzone) mask |= (uint)StormKeyMask.Left;
        if ((b & XINPUT_GAMEPAD_DPAD_UP) != 0 || state.Gamepad.sThumbLY > Deadzone) mask |= (uint)StormKeyMask.Up;
        if ((b & XINPUT_GAMEPAD_DPAD_DOWN) != 0 || state.Gamepad.sThumbLY < -Deadzone) mask |= (uint)StormKeyMask.Down;

        if ((b & XINPUT_GAMEPAD_RIGHT_SHOULDER) != 0 || state.Gamepad.bRightTrigger > 80) mask |= (uint)StormKeyMask.R;
        if ((b & XINPUT_GAMEPAD_LEFT_SHOULDER) != 0 || state.Gamepad.bLeftTrigger > 80) mask |= (uint)StormKeyMask.L;

        return mask;
    }

    public void SetRumble(float intensity0To1)
    {
        if (_xInputSetState == null) return;
        ushort motor = (ushort)Math.Clamp((int)(Math.Clamp(intensity0To1, 0f, 1f) * 65535f), 0, 65535);
        if (motor == _lastMotor) return;
        _lastMotor = motor;

        var vib = new XINPUT_VIBRATION
        {
            wLeftMotorSpeed = motor,
            wRightMotorSpeed = (ushort)(motor * 3 / 4)
        };
        _xInputSetState(0, &vib);
    }

    public void StopRumble() => SetRumble(0f);
}

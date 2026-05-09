# CC:CBC

A **CC: Tweaked** peripheral that adds computer control for cannons from **Create Big Cannons**.

## Features

- Computer control mode (`setComputerControl`) that disables default shaft-driven rotation.
- Two-axis aiming (`yaw`/`pitch`) with behavior tied to CBC kinetic speed.
- Assembly/disassembly control (`assemble`) and firing control (`fire`).
- Cannon telemetry via `getInfo()`.

## Lua API

Peripheral type: `cannon_mount`

| Method | Parameters | Returns | Description |
|---|---|---|---|
| `setComputerControl` | `enabled: boolean` | `nil` | Enables/disables computer control mode. |
| `isComputerControl` | - | `boolean` | Current computer control mode status. |
| `setTargetAngles` | `yaw: number`, `pitch: number` | `nil` | Sets target aiming angles. |
| `setTargetYaw` | `yaw: number` | `nil` | Sets horizontal angle only. |
| `setTargetPitch` | `pitch: number` | `nil` | Sets vertical angle only. |
| `getInfo` | - | `table` | Returns cannon telemetry/state. |
| `assemble` | `enabled: boolean` | `boolean` | `true` = assemble, `false` = disassemble. |
| `fire` | `enabled: boolean` | `boolean` | Controls fire signal (`true` keeps firing signal active). |

## `getInfo()` fields

| Field | Type | Description |
|---|---|---|
| `computerControl` | `boolean` | Whether computer control mode is active. |
| `assembled` | `boolean` | Whether the cannon is assembled on the mount. |
| `yaw` | `number` | Current horizontal angle (`0..360`). |
| `pitch` | `number` | Current vertical angle. |
| `targetYaw` | `number` | Target horizontal angle (`0..360`). |
| `targetPitch` | `number` | Target vertical angle. |
| `yawShaftSpeed` | `number` | Yaw interface shaft speed. |
| `pitchShaftSpeed` | `number` | Pitch interface shaft speed. |
| `x`, `y`, `z` | `number` | `Cannon Mount` position in the world. |
- `yaw` values in the API are presented in the `0..360` range to match in-game display.

# NATION FC

**Beyond The Game. Own The Pitch.**

NATION FC is an original mobile-first 3D football game project. This repository begins the playable prototype; it is not yet a finished commercial football game or an installable APK.

## Prototype 0.1 — First playable foundation

- Runtime-generated 3D football pitch, touchlines, halfway line, centre circle, goals and stadium stands.
- Player-controlled footballer with mobile on-screen movement controls and keyboard fallback.
- Physics-based ball with pass and shoot actions.
- Simple opponent AI that chases the ball.
- Goal detection and a match scoreboard.
- Camera follows the controlled player.
- Landscape mobile orientation.

## Open in Unity

1. Install Unity Hub and a Unity 6 editor with **Android Build Support**, Android SDK & NDK Tools, and OpenJDK.
2. Clone this repository and open the project folder in Unity Hub.
3. Open the scene named **NationFC** (create it from the menu: GameObject > 3D Object is not required; the bootstrap component builds the pitch at runtime).
4. Create an empty GameObject named `NationFC`, attach `Assets/Scripts/NationFCBootstrap.cs`, and press Play.
5. For Android, switch Build Settings to Android, set orientation to Landscape Left, and build an APK for device testing.

## Controls

- Move: on-screen directional buttons (or WASD / arrow keys in the Editor).
- PASS: short grounded pass.
- SHOOT: stronger forward shot.
- SPRINT: hold to move faster.

## Current scope and honest status

This is an early gameplay foundation. It uses original primitive placeholder models and generated materials. Realistic player models, animations, commentary, licensed teams, full match rules, menus, career mode, tournaments, online multiplayer, audio, optimisation, device testing, and store packaging are future milestones.

Do not use real club badges, player likenesses, league marks, or licensed audio without permission.

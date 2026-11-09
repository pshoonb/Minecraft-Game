# RPG Core 0.3.0 Prototype Acceptance Checklist

## Build and artifact trust
- [ ] Java 21 / NeoForge 21.1.265 build succeeds.
- [ ] Build artifact contains the mod JAR and SHA256SUMS.txt.
- [ ] SHA-256 listed in the workflow summary matches the downloaded JAR.

## Progression and persistence
- [ ] RPG level follows vanilla experience level.
- [ ] Attribute points follow the agreed cumulative formula.
- [ ] Level 100 grants 7,550 cumulative points; level 150 grants 15,675.
- [ ] Spending vanilla levels does not revoke earned attribute points.
- [ ] Logout/login and death/respawn preserve allocation and restore effects.
- [ ] Free reset removes all seven effects and refunds allocated points.

## Seven gameplay attributes
- [ ] Strength increases attack damage.
- [ ] Health increases maximum health.
- [ ] Defense reduces ordinary, fire/burning, and explosion damage and reduces explosion knockback.
- [ ] Mining Speed increases general block-break speed and suitable-tool mining efficiency.
- [ ] Luck increases vanilla Luck; verify using a loot table that reads player Luck.
- [ ] Speed increases movement speed on client and server.
- [ ] Jump Height increases actual jump height on client and server.
- [ ] Repeated application does not stack duplicate modifiers.
- [ ] Soft-cap curve has no 10,000-point hard cap.

## Multiplayer isolation
- [ ] Two players have independent attributes and effects.
- [ ] Allocating or resetting one player does not affect another.
- [ ] A client cannot allocate points to another player's UUID.

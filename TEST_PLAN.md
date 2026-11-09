# RPG Core 0.3.0 Prototype Acceptance Checklist

## Build and launch
- [ ] Java 21 / NeoForge 21.1.265 build succeeds.
- [ ] Build artifact contains the mod JAR and SHA256SUMS.txt.
- [ ] SHA-256 listed in the workflow summary matches the downloaded JAR.
- [ ] Client and dedicated server both start.

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
- [ ] Defense reduces ordinary, fire/burning, and explosion damage and reduces explosion knockback.\n- [ ] At more than 60% defense reduction, raw hits below the scaled threshold are cancelled before armor durability or absorption is consumed; intentional bypass-invulnerability damage remains unaffected.\n- [ ] Defense reduction approaches 90%, and the immunity threshold approaches 15 raw damage.
- [ ] Mining Speed increases general block-break speed and suitable-tool mining efficiency.
- [ ] Luck adds extra coal, diamond, emerald, lapis, redstone, copper, iron, gold, quartz, and ancient-debris resource drops.
- [ ] Silk Touch ore-block drops are not duplicated by Mining Luck.
- [ ] Fortune output is preserved when Mining Luck adds copies of the calculated resource drops.
- [ ] Speed increases movement speed on client and server.
- [ ] Jump Height increases actual jump height and simultaneously increases safe-fall distance and reduces fall damage.
- [ ] Repeated application does not stack duplicate modifiers.
- [ ] Soft-cap curve has no 10,000-point hard cap.

## Allocation buttons
- [ ] +1 allocates one point.
- [ ] +10 allocates up to ten points.
- [ ] +100 allocates up to one hundred points.
- [ ] When fewer points remain than requested, each button spends all remaining points rather than rejecting the allocation.

## Multiplayer isolation
- [ ] Two players have independent attributes and effects.
- [ ] Allocating or resetting one player does not affect another.
- [ ] A client cannot allocate points to another player's UUID.

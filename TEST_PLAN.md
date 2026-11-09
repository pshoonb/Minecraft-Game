# RPG Core 0.3.0 Prototype Acceptance Checklist

## Build and launch
- [ ] GitHub Actions build completes with Java 21 and NeoForge 21.1.265.
- [ ] JAR appears in the build artifact.
- [ ] Client starts with the prototype loaded.
- [ ] Dedicated server starts without loading client-only classes.

## Progression and persistence
- [ ] RPG level follows vanilla experience level.
- [ ] Attribute points follow the agreed cumulative formula.
- [ ] Spending vanilla levels does not revoke earned attribute points.
- [ ] Logout/login preserves allocation and restores gameplay modifiers.
- [ ] Death/respawn preserves allocation and restores gameplay modifiers.
- [ ] Dedicated-server restart preserves allocation.
- [ ] Free reset removes all seven effects and refunds allocated points.
- [ ] Updating from the earlier prototype preserves Strength and refunds removed prototype allocations.

## Seven gameplay attributes
- [ ] Strength increases attack damage by the documented value.
- [ ] Health increases maximum health.
- [ ] Defense reduces ordinary damage, fire/burning damage, and explosion damage; it also reduces burning duration and explosion knockback.
- [ ] Mining Speed increases general block-break speed and mining efficiency when using a suitable tool.
- [ ] Luck increases the vanilla Luck attribute; verify with a loot table that uses player Luck.
- [ ] Speed increases movement speed on the client and server.
- [ ] Jump Height increases actual jump height on the client and server.
- [ ] Attribute modifiers are visible on the owning client and authoritative server.
- [ ] Repeated allocations, resets, level changes, login, and respawn do not stack duplicate modifiers.
- [ ] No attribute has a 10,000-point hard cap; late points continue to provide diminishing returns.
- [ ] Lowering max health clamps current health to the new maximum.

## Multiplayer isolation
- [ ] Two players receive independent attachment data and gameplay modifiers.
- [ ] Allocating or resetting one player's attributes does not affect another player.
- [ ] A client cannot select another player's UUID in an allocation request.

# RPG Core 0.3.0 Prototype Acceptance Checklist

## Build and launch
- [ ] GitHub Actions build completes with Java 21 and NeoForge 21.1.265.
- [ ] JAR appears in the build artifact.
- [ ] Client starts with the prototype loaded.
- [ ] Dedicated server starts without attempting to load client-only classes.

## Progression and persistence
- [ ] RPG level follows vanilla experience level.
- [ ] Attribute points follow the agreed cumulative formula.
- [ ] Spending vanilla levels does not revoke earned attribute points.
- [ ] Logout/login preserves attribute allocation and restores gameplay modifiers.
- [ ] Death/respawn preserves attribute allocation and restores gameplay modifiers.
- [ ] Dedicated-server restart preserves attribute allocation.
- [ ] Free reset returns all seven allocations and removes their gameplay modifiers.
- [ ] Updating from the previous five-attribute prototype retains Strength and refunds the removed attribute allocations.

## Seven gameplay attributes
- [ ] Strength increases attack damage.
- [ ] Health increases maximum health.
- [ ] Defense increases armor.
- [ ] Mining Speed increases block-break speed.
- [ ] Luck increases the vanilla Luck attribute.
- [ ] Speed increases movement speed.
- [ ] Jump Height increases the upward velocity of player jumps.
- [ ] Repeated allocations, resets, level changes, login, and respawn do not duplicate transient modifiers.
- [ ] Lowering maximum health clamps current health to the new maximum.

## Multiplayer isolation
- [ ] Two players receive independent attachment data and gameplay modifiers.
- [ ] Allocating or resetting one player's attributes does not affect another player.
- [ ] A client cannot select another player's UUID in an allocation request.

# RPG Core 0.3.0 Prototype Acceptance Checklist

## Build and launch
- [ ] GitHub Actions build completes with Java 21 and NeoForge 21.1.265.
- [ ] JAR appears in the build artifact.
- [ ] Client starts with the prototype loaded.
- [ ] Dedicated server starts without attempting to load client-only classes.

## Progression and persistence
- [ ] RPG level follows vanilla experience level.
- [ ] Attribute points follow the agreed cumulative formula.
- [ ] Spending vanilla levels lowers current-level bonuses but does not revoke earned attribute points.
- [ ] Logout/login preserves attribute allocation and restores gameplay modifiers.
- [ ] Death/respawn preserves attribute allocation and restores gameplay modifiers.
- [ ] Dedicated-server restart preserves attribute allocation.
- [ ] Free reset returns all allocated points and removes attribute modifiers.

## Gameplay effects
- [ ] Level increases maximum health and armor.
- [ ] Agility increases movement speed.
- [ ] Constitution increases maximum health and armor.
- [ ] Lowering current level or resetting Constitution clamps current health to the new maximum.
- [ ] Repeated allocations, resets, level changes, login, and respawn do not duplicate modifiers.
- [ ] Strength, Intelligence, and Spirit remain saved/allocatable without granting unimplemented effects.

## Multiplayer isolation
- [ ] Two players receive independent attachment data and gameplay modifiers.
- [ ] Allocating or resetting one player's attributes does not affect another player.
- [ ] A client cannot select another player's UUID in an allocation request.

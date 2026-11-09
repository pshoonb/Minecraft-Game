# Prototype Acceptance Checklist

## Build and launch
- [ ] Build completes with Java 21 and NeoForge 21.1.265.
- [ ] JAR appears in `build/libs/`.
- [ ] Client starts with the prototype loaded.
- [ ] Dedicated server starts without attempting to load client-only classes.
- [ ] Both sides report the same mod version.

## Per-player persistence
- [ ] Open the player inventory and verify the RPG Attributes button appears below the inventory panel.
- [ ] Click the button and verify the RPG panel opens.
- [ ] Close the RPG panel and verify it returns to the inventory screen.
- [ ] Verify the P key no longer opens the RPG panel and does not interfere with vanilla controls.
- [ ] Opening the RPG panel requests a server snapshot.
- [ ] Incrementing the diagnostic counter changes only server-side attachment data.
- [ ] Reopening the screen displays the updated counter.
- [ ] Logout/login preserves the counter.
- [ ] Death/respawn preserves the counter.
- [ ] Dedicated-server restart preserves the counter.
- [ ] Switching to another server clears the old client's cached snapshot.

## Multiplayer isolation
- [ ] Two different UUIDs receive independent attachment values.
- [ ] Player A's increment does not change player B's value.
- [ ] Player B's increment does not change player A's value.
- [ ] A client cannot choose a target UUID in the increment request.
- [ ] Only the connected player receives their own sync snapshot.

## Experience loot
- [ ] The sixth attribute, Experience Loot, appears in the attribute screen and can be allocated.
- [ ] Existing player attachment data loads with Experience Loot defaulting to zero.
- [ ] Only server-side, successfully applied player damage adds a participant.
- [ ] Direct player attacks and player-owned projectiles are both attributed to the player.
- [ ] Repeated damage by one player refreshes their 60-second participation timer without duplicating their entry.
- [ ] A participant is excluded once 1,200 game ticks have elapsed since their last successful hit.
- [ ] Multiplayer kills use the highest Experience Loot value among active online participants, not only the final attacker.
- [ ] The bonus is applied to the entity's existing XP drop once, with fractional results rounded down.
- [ ] Entities with zero original XP do not create XP through Experience Loot.
- [ ] Participation records are removed after XP-drop handling and expire during server ticking.

## Follow-up gameplay tests (not implemented in this prototype)
- [ ] Health maximum/current-health behavior.
- [ ] Defense damage paths and other-mod ordering.
- [ ] Attack damage, critical hits, enchantments and cooldown.
- [ ] Speed in land/water, sneak/sprint, flight and vehicles.
- [ ] Jump height plus Jump Boost.
- [ ] Luck kill attribution and duplicate-drop prevention.
- [ ] Mining speed across tools, block hardness and server correction.
- [ ] RPG menu and HUD at several GUI scales/resolutions.

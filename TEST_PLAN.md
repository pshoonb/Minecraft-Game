# Prototype Acceptance Checklist

## Build and launch
- [ ] Build completes with Java 21 and NeoForge 21.1.265.
- [ ] JAR appears in `build/libs/`.
- [ ] Client starts with the prototype loaded.
- [ ] Dedicated server starts without attempting to load client-only classes.
- [ ] Both sides report the same mod version.

## Per-player persistence and UI
- [ ] Open the player inventory and verify the RPG Attributes button appears below the inventory panel.
- [ ] Click the button and verify the RPG panel opens.
- [ ] Close the RPG panel and verify it returns to the inventory screen.
- [ ] Verify the P key no longer opens the RPG panel and does not interfere with vanilla controls.
- [ ] Opening the RPG panel requests a server snapshot.
- [ ] The sixth attribute, Experience Loot, appears and can be allocated.
- [ ] Existing player attachment data loads with Experience Loot defaulting to zero.
- [ ] Logout/login, death/respawn and dedicated-server restart preserve the allocated attribute values.

## Combat participation and offline eligibility
- [ ] Only server-side, successfully applied player damage adds a participant.
- [ ] Direct player attacks and player-owned projectiles are both attributed to the player.
- [ ] Repeated damage by one player refreshes their 60-second timer without duplicating their entry.
- [ ] A participant is excluded once 1,200 game ticks have elapsed since their last successful hit.
- [ ] Logging out does not remove a participant from any still-active entity record.
- [ ] PlayerLoggedOutEvent captures the participant's latest Experience Loot attribute before the player is removed/saved.
- [ ] An offline participant remains eligible until their last successful hit is 1,200 ticks old.
- [ ] At LivingDeathEvent, online participants are read live and offline participants use their latest logout snapshot.
- [ ] An attribute change after the last hit but before logout is reflected in the offline participant's value.
- [ ] A player who logs back in before the target dies is evaluated using their current online attribute.
- [ ] Changing an attribute after the target has died does not change the stored value used by subsequent XP batches.
- [ ] A death canceled by another event listener does not leave a valid death snapshot.

## Experience-orb denomination calculation
- [ ] Vanilla denomination counting uses 2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1 in descending greedy order.
- [ ] For S=6, N=2 (3+3); for S=10, N=2 (7+3); for S=960, N=6 (617+307+17+17+1+1).
- [ ] P is floor(S * (1 + 2% * L)).
- [ ] Q is P // (max(2, N) - 1).
- [ ] Every logical orb in the batch gets denomination Q; the logical orb count remains N.
- [ ] N=0 creates no orbs; N=1 and N=2 both use divisor 1.
- [ ] The calculation rounds down and uses overflow-safe integer arithmetic.
- [ ] A computed Q above 32767 is capped at 32767 so the saved short Value cannot turn negative.

## Drop ownership and merging
- [ ] Only the current LivingExperienceDropEvent batch is canceled and replaced.
- [ ] The vanilla XP batch is not also emitted (no duplicate reward).
- [ ] No nearby existing ExperienceOrb is scanned, selected, or rewritten by this mod.
- [ ] A new orb merging with an existing orb of the same denomination only increases the merged entity's count; the denomination remains unchanged.
- [ ] Existing orbs with different denominations are unchanged.
- [ ] Other entities' later XP drops are independently calculated using only their own participant snapshots.
- [ ] Ender Dragon's multiple XP award batches are all processed separately and retain their vanilla timing.
- [ ] Entities with zero XP do not create XP through Experience Loot.
- [ ] Mining, smelting, and other non-LivingExperienceDropEvent XP sources remain unchanged.
- [ ] Entity tracking data is cleared when the living entity leaves its level.

## Follow-up gameplay tests (not implemented in this prototype)
- [ ] Health maximum/current-health behavior.
- [ ] Defense damage paths and other-mod ordering.
- [ ] Attack damage, critical hits, enchantments and cooldown.
- [ ] Speed in land/water, sneak/sprint, flight and vehicles.
- [ ] Jump height plus Jump Boost.
- [ ] Luck kill attribution and duplicate-drop prevention.
- [ ] Mining speed across tools, block hardness and server correction.
- [ ] RPG menu and HUD at several GUI scales/resolutions.

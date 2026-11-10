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
- [ ] Opening the RPG panel requests a server snapshot.
- [ ] Exactly eight attributes appear in this order: Strength, Defense, Health, Mining Speed, Movement Speed, Jump Height, Luck, Experience Loot.
- [ ] Legacy placeholder attributes Agility, Constitution, Intelligence and Spirit no longer appear.
- [ ] All eight attributes can be allocated and reset.
- [ ] Each attribute row displays the allocated point count and an effect description.
- [ ] All eight attributes display their current calculated total effect using the same formula calculator as the gameplay code.
- [ ] Percentage effects show the numeric value and literal percent sign correctly in both Simplified Chinese and English.
- [ ] The RPG Attributes screen fills the available game window instead of displaying a fixed 410x236 center panel.
- [ ] The responsive two-column layout keeps all eight attributes, allocation controls, effect text, reset and back controls visible at common window sizes and GUI scales.
- [ ] Each attribute has +1 and +10 in the upper button row, with +50 directly below +1 and +100 directly below +10.
- [ ] Each +N allocation button adds min(N, available points); for example, +50 with 7 points remaining allocates exactly 7 points.
- [ ] The layout remains readable in Simplified Chinese and English at multiple GUI scales and window aspect ratios.
- [ ] The inventory screen button opens the full-window RPG Attributes screen.
- [ ] The Back button and Escape return to the inventory screen that opened the RPG Attributes screen.
- [ ] No P-key or other key mapping opens the RPG Attributes screen; the old key mapping is absent from Controls.
- [ ] Attribute attachment data persists through logout/login, death/respawn and server restart.

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
- [ ] Entities with zero XP do not create orbs through Experience Loot.
- [ ] Mining, smelting, and other non-LivingExperienceDropEvent XP sources remain unchanged.
- [ ] Entity tracking data is cleared when the living entity leaves its level.

## Luck loot coverage
- [ ] With 0 Luck, player-caused mob loot matches vanilla pool rolls and random-chance predicates.
- [ ] With Luck, each eligible living-entity pool with a positive configured base roll count has at most one additional roll; its chance is min(100%, the cumulative Luck percentage).
- [ ] The additional roll uses the same vanilla weighted selection; entry weights and relative weight proportions are unchanged.
- [ ] A guaranteed one-entry pool still produces its ordinary guaranteed drop, and may produce one more copy when the extra roll succeeds.
- [ ] Pool and entry conditions are still evaluated by vanilla for the additional roll.
- [ ] Vanilla random_chance and random_chance_with_enchanted_bonus predicates receive the relative Luck probability multiplier, including during the additional roll.
- [ ] Direct player kills and player-owned projectile kills both resolve the correct player's Luck.
- [ ] Loot contexts without a resolvable player attacker receive no custom Luck pool bonus.
- [ ] Worn-equipment drop chances still use the Luck percentage modifier.

## Follow-up gameplay tests
- [ ] Strength affects attack damage along approved balance rules.
- [ ] Defense reduces incoming damage along approved balance rules.
- [ ] Health changes max health and handles current health correctly.
- [ ] Mining Speed affects both client-side dig progress and server-side block breaking without client/server desynchronization.
- [ ] Movement Speed works on land and in water, while sneaking, sprinting, flying and riding.
- [ ] Jump Height works with the vanilla Jump Boost effect.
- [ ] Luck's ore, chance-predicate, weighted-pool and deterministic-entry behavior produces no unintended duplicate rewards.

## Windows installer EXE packaging
- [ ] The Windows workflow checks out the exact commit used by the successful mod build.
- [ ] The Python installer source passes `py_compile`.
- [ ] The single-file GUI EXE is generated as `RPG-Core-Installer.exe`.
- [ ] The matching NeoForge 21.1.265 installer JAR is bundled into the EXE and can be found from the PyInstaller one-file extraction directory.
- [ ] Running the EXE uses the executable's folder for optional local files and uses the bundled NeoForge installer as a fallback.
- [ ] The frozen EXE does not attempt to replace itself with the raw Python source during the legacy Python self-update check.
- [ ] The EXE and SHA-256 checksum are uploaded as a workflow artifact and attached to the GitHub Release created for the same source commit.

<h1 align="left">jeff mod++ | 32766's fork for 9b</h1>

###

<p align="left">Here you'll be able to find jeff mod updated to 1.21.5, with some modifications to the module.<br>Everything is tested on 9b9t.<br>Won't answer any issue or message, if you feel to change something, just fork it and do your things</p>

###
## Dependencies

- Meteor 1.21.5
- Xaero Minimap
- Xaero Worldmap
- Xaero Plus

## Features
- Every modules from jeff mod are included
- StashMover modules
    - Added and updated to 1.21.5 from https://github.com/miles352/meteor-stash-mover
    - Optimized to be faster
    - Deposit Chest has been configured for faster operations (stash model to build: https://www.youtube.com/watch?v=R78TuVL54IE)
    - No more deposit chunk to configure
    - Endermite management on pearl load
    - Better management of pearl statis chamber to prevent water blocked
    - Working on (i think) all server support whispering
    - Need to work with : KillAura (any mode to kill potential endermites), a backup pearl (rare case when pearls are not thrown correctly), Baritone AssumeWalkOnWater = true
- ChatSigns
    - Display signs reachable in your render-distance in chat
    - TO:DO Click on the desired signs to enable a tracer
- ElytraReplace
    - Swap an almost broken Elytra with a new one automatically
    - Useful if your wings have cursed enchantments
    - Configurable durability treshold (default: 5)
    - (not compatible with Armor Storage from Inventory Tweaks)
- NoKillAuraFly
    - Disable KillAura during Elytra flights
    - Enable it automatically when on ground
    - Useful during nether baritone travel, to avoid ghasts and piglin triggers

## Prerequisites

- **JDK 17+**: [Adoptium](https://adoptium.net/) (JRE is ok if you don't build it urself)
- **Git**: [Download](https://git-scm.com/downloads) (only to build it urself)
- **Fabric Loader/API**: [FabricMC](https://fabricmc.net/use/installer/)

## Build Instructions

1. **Clone Repository**:
   ```bash
   git clone https://github.com/WisdomGhoul/jeff-mod-plusplus
   cd jeff-mod-plusplus
   ```

2. **Build JAR**:
   ```bash
   ./gradlew build
   ```
   JAR in `build/libs/` (e.g., `jeff-mod-plusplus.jar`).

3. **Install**:
   Copy JAR to `.minecraft/mods/`. Launch Minecraft with Fabric + Meteor 1.21.5.

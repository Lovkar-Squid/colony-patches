package me.lovkar.colonypatches.harness;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.ChangeStorage;
import com.ldtteam.structurize.util.ITickedWorldOperation;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import steve_gall.minecolonies_compatibility.api.common.plant.CustomizedCrop;
import steve_gall.minecolonies_compatibility.api.common.plant.PlantBlockContext;
import steve_gall.minecolonies_compatibility.api.common.plant.PlantSeedContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Headless check of a MineColonies farmer working Cultural Delights crops, with the
 * MineColonies Compatibility addon loaded, on a copy of the modpack's server mods.
 *
 * {@code corntest.txt} in the server folder holds the words: {@code crop=corn} (a field of ripe
 * three-block corn) or {@code crop=mixed} (ripe eggplants, cucumbers and beans), plus optional
 * {@code rate=<ticks per second>}. The test builds a colony with a farmer and one field, asks the
 * compatibility addon about every planted crop the way its farmer mixin does, then lets the farmer
 * harvest. It logs {@code [corntest] STEP / CHECK / RESULT} lines and stops the server itself; a crash
 * of the server is the other possible ending.
 */
@Mod("corntest")
public final class CornTest {
    private static final Logger LOG = LoggerFactory.getLogger("corntest");
    private static final String TAG = "[corntest] ";
    private static final int RADIUS = 2;
    private static final List<String> PRODUCE = List.of("culturaldelights:corn_cob", "culturaldelights:eggplant",
            "culturaldelights:white_eggplant", "culturaldelights:cucumber", "culturaldelights:bean_pod");

    @FunctionalInterface
    private interface Body {
        boolean run(ServerLevel level) throws Exception;
    }

    private record Step(String name, int timeoutTicks, Body body) {}

    private final String mode = readMode();
    private final String crop = word("crop=", "corn");
    private final List<Step> steps = new ArrayList<>();
    private final Map<BlockPos, Blueprint> blueprints = new HashMap<>();
    private final Map<BlockPos, String[]> sources = new HashMap<>();
    private final Set<BlockPos> pasting = new HashSet<>();
    private final Map<BlockPos, String> planted = new LinkedHashMap<>();
    private int tick;
    private int index = -1;
    private int stepStart;
    private int failures;
    private boolean finished;

    private IColony colony;
    private ServerPlayer owner;
    private Set<ServerPlayer> subscribers;
    private BlockPos center;
    private IBuilding farmerHut;
    private ICitizenData farmer;
    private int lastReport;

    public CornTest(final IEventBus modBus) {
        if (mode.isEmpty()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(this::onTick);
        steps.add(new Step("make the world and the colony", 20, this::makeWorld));
        steps.add(new Step("colony active", 1200, l -> {
            keepColonyActive();
            return colony.isActive();
        }));
        steps.add(new Step("paste the town hall (Medieval Oak, level 1)", 20,
                l -> paste(l, "Medieval Oak", "fundamentals/townhall1.blueprint", center)));
        steps.add(new Step("town hall pasted", 6000, l -> pasted(l, center)));
        steps.add(new Step("register the town hall", 200, l -> register(l, center, "town hall") != null));
        steps.add(new Step("paste the farmer's hut (Medieval Oak, level 1)", 20,
                l -> paste(l, "Medieval Oak", "agriculture/horticulture/farmer1.blueprint", farmerPos())));
        steps.add(new Step("farmer's hut pasted", 6000, l -> pasted(l, farmerPos())));
        steps.add(new Step("register the farmer's hut", 200, l -> (farmerHut = register(l, farmerPos(), "farmer's hut")) != null));
        steps.add(new Step("hire a farmer", 200, l -> {
            farmer = hire(l, farmerHut);
            final String job = farmer.getJob() == null ? "none" : farmer.getJob().getClass().getSimpleName();
            check("job JobFarmer", job.equals("JobFarmer"), farmer.getName() + " -> " + job);
            return true;
        }));
        steps.add(new Step("the farmer stands in the world", 1200, l -> farmer.getEntity().isPresent()));
        steps.add(new Step("lay out a field of ripe " + crop, 20, this::layOutField));
        steps.add(new Step("the farmer harvests the field", 12000, this::harvested));
        if (crop.equals("corn")) {
            steps.add(new Step("the farmer plants corn again", 12000, this::replanted));
        }
        steps.add(new Step("no crash", 20, l -> {
            check("server still running after the harvest", true, "tick " + tick);
            return true;
        }));
    }

    // ---------------------------------------------------------------- the field

    private BlockPos farmerPos() {
        return center.offset(28, 0, 0);
    }

    /** The scarecrow, on open ground south of the huts. */
    private BlockPos fieldPos() {
        return center.offset(14, 0, 30);
    }

    private boolean layOutField(final ServerLevel level) {
        final BlockPos f = fieldPos();
        final Block scarecrow = block("minecolonies:blockhutfield");
        final BlockState lower = scarecrow.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        level.setBlock(f, lower, 3);
        level.setBlock(f.above(), lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 3);

        final String[] kinds = crop.equals("corn") ? new String[] {"corn"} : new String[] {"eggplants", "cucumbers", "beans"};
        int n = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                final BlockPos c = f.offset(dx, 0, dz);
                for (int up = 0; up < 4; up++) {
                    level.setBlock(c.above(up), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(c.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
                final String kind = kinds[n++ % kinds.length];
                plantRipe(level, c, kind);
                planted.put(c.immutable(), kind);
            }
        }

        final Item seed = item(crop.equals("corn") ? "culturaldelights:corn_kernels" : "culturaldelights:eggplant_seeds");
        FarmField field = FarmField.create(f, level);
        for (final Direction d : Direction.Plane.HORIZONTAL) {
            field.setRadius(d, RADIUS);
        }
        field.setSeed(new ItemStack(seed));
        field.setFieldStage(FarmField.Stage.PLANTED);
        final boolean added = colony.getServerBuildingManager().addBuildingExtension(field);
        final Optional<IBuildingExtension> registered = colony.getServerBuildingManager().getMatchingBuildingExtension(e -> e.getPosition().equals(f));
        if (registered.isPresent() && registered.get() instanceof FarmField reg && reg != field) {
            field = reg;
            for (final Direction d : Direction.Plane.HORIZONTAL) {
                field.setRadius(d, RADIUS);
            }
            field.setSeed(new ItemStack(seed));
            field.setFieldStage(FarmField.Stage.PLANTED);
        }
        final BuildingExtensionsModule module = farmerHut.getFirstModuleOccurance(BuildingExtensionsModule.class);
        final boolean assigned = module != null && module.assignExtension(field);
        check("field registered and assigned", added && assigned && registered.isPresent(),
                "added " + added + ", registered " + registered.isPresent() + ", assigned " + assigned + ", " + planted.size()
                        + " ripe plants of " + String.join("/", kinds) + ", stage " + field.getFieldStage());

        // tools, seed and food, so nothing has to be requested first
        give(farmer, new ItemStack(Items.WOODEN_HOE));
        give(farmer, new ItemStack(Items.STONE_HOE));
        give(farmer, new ItemStack(seed, 32));
        give(farmer, new ItemStack(Items.BREAD, 16));

        probe(level, seed);
        lastReport = tick;
        return true;
    }

    private void plantRipe(final ServerLevel level, final BlockPos c, final String kind) {
        final Block b = block("culturaldelights:" + kind);
        if (kind.equals("corn")) {
            final Property<?> age = b.getStateDefinition().getProperty("age");
            final Property<?> height = b.getStateDefinition().getProperty("height");
            for (int h = 0; h <= 2; h++) {
                level.setBlock(c.above(h), with(with(b.defaultBlockState(), age, 7), height, h), 2);
            }
            return;
        }
        if (b instanceof CropBlock crop) {
            level.setBlock(c, crop.getStateForAge(crop.getMaxAge()), 2);
        } else {
            check("plant " + kind, false, b + " is not a crop block");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(final BlockState state, final Property<?> property, final int value) {
        return state.setValue((IntegerProperty) property, (Integer) value);
    }

    /**
     * What the compatibility addon's farmer mixin asks for each planted kind: which customized crop
     * claims the block (findHarvestableSurface, harvestIfAble) and the seed (plantCrop), and where the
     * crop wants to be harvested.
     */
    private void probe(final ServerLevel level, final Item seed) {
        final Set<String> seen = new HashSet<>();
        for (final Map.Entry<BlockPos, String> e : planted.entrySet()) {
            if (!seen.add(e.getValue())) {
                continue;
            }
            final BlockPos c = e.getKey();
            final PlantBlockContext ctx = new PlantBlockContext(level, c, level.getBlockState(c));
            String handler = "none";
            String outcome;
            boolean ok = true;
            try {
                final CustomizedCrop custom = CustomizedCrop.selectByCrop(ctx);
                handler = custom == null ? "none (MineColonies' own crop handling)" : custom.getClass().getName();
                final CustomizedCrop.SpecialHarvestPositionFunction fn = custom == null ? null : custom.getSpecialHarvestPosition(ctx);
                outcome = fn == null ? "no special harvest position" : "special harvest position " + fn.apply(ctx);
            } catch (final Throwable t) {
                ok = false;
                outcome = t.toString();
            }
            check("compat addon on ripe " + e.getValue(), ok, handler + ": " + outcome);
        }
        try {
            final PlantSeedContext sctx = new PlantSeedContext(level, fieldPos().offset(RADIUS, 0, RADIUS), new ItemStack(seed));
            final CustomizedCrop bySeed = CustomizedCrop.selectBySeed(sctx);
            check("compat addon on the seed " + BuiltInRegistries.ITEM.getKey(seed), true,
                    bySeed == null ? "none" : bySeed.getClass().getName() + ", plant state " + bySeed.getPlantState(sctx));
        } catch (final Throwable t) {
            check("compat addon on the seed " + BuiltInRegistries.ITEM.getKey(seed), false, t.toString());
        }
    }

    private int ripeLeft(final ServerLevel level) {
        int ripe = 0;
        for (final BlockPos c : planted.keySet()) {
            final BlockState s = level.getBlockState(c);
            if (s.getBlock() instanceof CropBlock cb && cb.isMaxAge(s)) {
                ripe++;
            }
        }
        return ripe;
    }

    private Map<String, Integer> produce() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String id : PRODUCE) {
            final Item it = item(id);
            final int n = countWorker(farmer, s -> s.is(it)) + countBuilding(farmerHut, s -> s.is(it));
            if (n > 0) {
                counts.put(id.substring(id.indexOf(':') + 1), n);
            }
        }
        return counts;
    }

    private boolean harvested(final ServerLevel level) {
        final int ripe = ripeLeft(level);
        final Map<String, Integer> got = produce();
        if (tick - lastReport >= 200) {
            lastReport = tick;
            LOG.info(TAG + "PROGRESS tick {}: {} ripe left of {}, produce {}, farmer {}", tick, ripe, planted.size(), got, who(farmer));
        }
        if (ripe == 0) {
            check("every ripe plant harvested", true, planted.size() + " plants, produce " + got);
            check("produce reached the farmer", !got.isEmpty(), got.toString());
            if (crop.equals("corn")) {
                // the upper corn blocks must go with the bottom one. A cell may already hold new corn (the
                // farmer replants as he goes), so: no corn above what the cell's own corn is tall enough for
                // (CornBlock.getExpectedMaxHeight: age 0-2 one block, 3-4 two, 5-7 three)
                int hanging = 0;
                final Block corn = block("culturaldelights:corn");
                final IntegerProperty age = (IntegerProperty) corn.getStateDefinition().getProperty("age");
                final Map<String, Integer> cells = new LinkedHashMap<>();
                for (final BlockPos c : planted.keySet()) {
                    final BlockState bottom = level.getBlockState(c);
                    final int a = bottom.is(corn) ? bottom.getValue(age) : -1;
                    final int tall = a >= 5 ? 2 : a >= 3 ? 1 : 0;
                    cells.merge(a < 0 ? "empty" : "corn age " + a, 1, Integer::sum);
                    for (int h = tall + 1; h <= 2; h++) {
                        if (level.getBlockState(c.above(h)).is(corn)) {
                            hanging++;
                            break;
                        }
                    }
                }
                check("no corn left hanging over a harvested cell", hanging == 0, hanging + " of " + planted.size() + ", cells " + cells);
            }
            lastReport = tick;
            return true;
        }
        return false;
    }

    /** After the harvest the field goes round again: the farmer plants corn kernels on it. */
    private boolean replanted(final ServerLevel level) {
        final Block corn = block("culturaldelights:corn");
        int young = 0;
        for (final BlockPos c : planted.keySet()) {
            final BlockState s = level.getBlockState(c);
            if (s.is(corn) && s.getBlock() instanceof CropBlock cb && !cb.isMaxAge(s)) {
                young++;
            }
        }
        if (tick - lastReport >= 200) {
            lastReport = tick;
            LOG.info(TAG + "PROGRESS tick {}: {} cells with young corn, farmer {}", tick, young, who(farmer));
        }
        if (young >= planted.size() / 2) {
            check("corn planted again", true, young + " of " + planted.size() + " cells, kernels left "
                    + countWorker(farmer, s -> s.is(item("culturaldelights:corn_kernels"))));
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- colony (as in the Runesmith harness)

    private boolean makeWorld(final ServerLevel level) {
        final MinecraftServer server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(1000);
        final float rate = Float.parseFloat(word("rate=", "20"));
        if (rate != 20F) {
            server.tickRateManager().setTickRate(rate);
        }
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        center = new BlockPos(0, y, 0);
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        colony = IColonyManager.getInstance().createColony(level, center, owner, "Corn Test", "Medieval Oak");
        check("colony created", colony != null, "colony at " + center + ", tick rate " + rate + ", crop " + crop);
        return true;
    }

    /** MineColonies refuses fake players as close subscribers; without one the colony stays inactive. */
    @SuppressWarnings("unchecked")
    private void keepColonyActive() {
        if (colony == null || owner == null) {
            return;
        }
        try {
            if (subscribers == null) {
                final java.lang.reflect.Field f = colony.getPackageManager().getClass().getDeclaredField("closeSubscribers");
                f.setAccessible(true);
                subscribers = (Set<ServerPlayer>) f.get(colony.getPackageManager());
            }
            subscribers.add(owner);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            LOG.warn(TAG + "could not keep the colony active: {}", e.toString());
        }
    }

    private boolean paste(final ServerLevel level, final String pack, final String path, final BlockPos pos) {
        final Blueprint bp = StructurePacks.getBlueprint(pack, path, level.registryAccess());
        if (bp == null) {
            check("blueprint " + pack + "/" + path, false, "not found");
            return true;
        }
        bp.setRotationMirror(RotationMirror.NONE, level);
        blueprints.put(pos, bp);
        sources.put(pos, new String[] {pack, path});
        final CreativeStructureHandler handler = new CreativeStructureHandler(level, pos, bp, RotationMirror.NONE, false);
        final PlaceStructureOperation op = new PlaceStructureOperation(new StructurePlacer(handler), FakePlayerFactory.getMinecraft(level));
        pasting.add(pos);
        Manager.addToQueue(new ITickedWorldOperation() {
            @Override
            public boolean apply(final ServerLevel l) {
                final boolean done = op.apply(l);
                if (done) {
                    pasting.remove(pos);
                }
                return done;
            }

            @Override
            public ChangeStorage getChangeStorage() {
                return op.getChangeStorage();
            }
        });
        return true;
    }

    private boolean pasted(final ServerLevel level, final BlockPos pos) {
        return !pasting.contains(pos) && level.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding;
    }

    private IBuilding register(final ServerLevel level, final BlockPos pos, final String what) {
        if (!(level.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding hut)) {
            check("register " + what, false, "no hut block entity at " + pos);
            return null;
        }
        final String[] source = sources.get(pos);
        if (source != null && hut instanceof IBlueprintDataProviderBE data) {
            data.setPackName(source[0]);
            data.setBlueprintPath(source[1]);
        }
        if (hut instanceof TileEntityColonyBuilding te && level.getBlockState(pos).getBlock() instanceof AbstractColonyBlock<?> b) {
            te.registryName = b.getBuildingEntry().getRegistryName();
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null) {
            building = colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        if (building == null) {
            check("register " + what, false, "addNewBuilding returned null");
            return null;
        }
        if (building.getBuildingLevel() < 1) {
            building.upgradeBuildingLevelToSchematicData();
        }
        if (building.getBuildingLevel() < 1) {
            building.setBuildingLevel(1);
            building.onUpgradeComplete(blueprints.get(pos), 1);
        }
        final var corners = building.getCorners();
        final Block solidFill = block("structurize:blocksolidsubstitution");
        final Block keep = block("structurize:blocksubstitution");
        if (corners != null) {
            for (final BlockPos p : BlockPos.betweenClosed(corners.getA(), corners.getB())) {
                if (level.getBlockEntity(p) instanceof com.minecolonies.api.tileentities.AbstractTileEntityRack
                        && !building.getContainers().contains(p)) {
                    building.addContainerPosition(p.immutable());
                }
                final BlockState s = level.getBlockState(p);
                if (s.is(solidFill) || s.is(keep)) {
                    final boolean ground = s.is(solidFill) || p.getY() < pos.getY();
                    level.setBlock(p, ground ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        check("register " + what, building.getBuildingLevel() >= 1, building.getClass().getSimpleName() + " level "
                + building.getBuildingLevel() + " at " + pos);
        return building;
    }

    private ICitizenData hire(final ServerLevel level, final IBuilding building) {
        final WorkerBuildingModule module = building.getFirstModuleOccurance(WorkerBuildingModule.class);
        if (module != null && !module.getAssignedCitizen().isEmpty()) {
            return module.getAssignedCitizen().get(0);
        }
        final ICitizenData data = colony.getCitizenManager().createAndRegisterCivilianData();
        final int x = center.getX() + 14;
        final int z = center.getZ() + 4;
        colony.getCitizenManager().spawnOrCreateCitizen(data, level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
        final boolean ok = module != null && module.assignCitizen(data);
        LOG.info(TAG + "{} hired into {}: {}", data.getName(), building.getSchematicName(), ok);
        return data;
    }

    private String who(final ICitizenData data) {
        if (data == null) {
            return "nobody";
        }
        final Optional<AbstractEntityCitizen> e = data.getEntity();
        final Object ai = data.getJob() == null ? null : data.getJob().getWorkerAI();
        final String state = ai instanceof ITickingStateAI t ? String.valueOf(t.getState()) : "-";
        if (e.isEmpty()) {
            return data.getName() + " without an entity, state " + state;
        }
        return data.getName() + " at " + e.get().blockPosition().toShortString() + ", state " + state;
    }

    private static boolean give(final ICitizenData data, final ItemStack stack) {
        final Optional<AbstractEntityCitizen> entity = data.getEntity();
        return entity.isPresent() && InventoryUtils.addItemStackToItemHandler(entity.get().getInventoryCitizen(), stack);
    }

    private static int countBuilding(final IBuilding building, final java.util.function.Predicate<ItemStack> what) {
        final IItemHandler racks = building.getItemHandlerCap((Direction) null);
        return racks == null ? 0 : InventoryUtils.getItemCountInItemHandler(racks, what);
    }

    private static int countWorker(final ICitizenData data, final java.util.function.Predicate<ItemStack> what) {
        return data == null ? 0 : InventoryUtils.getItemCountInItemHandler(data.getInventory(), what);
    }

    private static Block block(final String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static Item item(final String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    // ---------------------------------------------------------------- runner

    private void onTick(final ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        tick++;
        if (tick < 40) {
            return;
        }
        final ServerLevel level = event.getServer().overworld();
        try {
            if (tick % 20 == 0) {
                keepColonyActive();
            }
            if (index < 0) {
                index = 0;
                begin();
            }
            final Step step = steps.get(index);
            if (step.body().run(level)) {
                index++;
                if (index >= steps.size()) {
                    finish(level);
                    return;
                }
                begin();
            } else if (tick - stepStart > step.timeoutTicks()) {
                check(step.name(), false, "timed out after " + step.timeoutTicks() + " ticks; " + ripeLeft(level) + " ripe left, produce "
                        + (farmer == null ? "-" : produce()) + ", farmer " + who(farmer));
                finish(level);
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION in step '" + (index >= 0 && index < steps.size() ? steps.get(index).name() : "?") + "'", t);
            check("no exception", false, t.toString());
            finish(level);
        }
    }

    private void begin() {
        stepStart = tick;
        LOG.info(TAG + "STEP {}/{} {} (tick {})", index + 1, steps.size(), steps.get(index).name(), tick);
    }

    private void check(final String name, final boolean ok, final String detail) {
        if (!ok) {
            failures++;
        }
        LOG.info(TAG + "CHECK {}: {} {}", name, ok ? "OK" : "FAIL", detail);
    }

    private void finish(final ServerLevel level) {
        finished = true;
        LOG.info(TAG + "RESULT {}: {}", crop, failures == 0 ? "PASS" : "FAIL");
        final MinecraftServer server = level.getServer();
        server.execute(() -> server.halt(false));
    }

    private String word(final String key, final String fallback) {
        for (final String w : mode.split("\\s+")) {
            if (w.startsWith(key)) {
                return w.substring(key.length());
            }
        }
        return fallback;
    }

    private static String readMode() {
        try {
            return Files.readString(Path.of("corntest.txt")).trim().toLowerCase();
        } catch (final Exception e) {
            return "";
        }
    }
}

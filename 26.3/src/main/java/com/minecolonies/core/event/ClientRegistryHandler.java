package com.minecolonies.core.event;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.client.ModKeyMappings;
import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.crafting.registry.ModRecipeSerializer;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.*;
import com.minecolonies.core.client.model.raiders.*;
import com.minecolonies.core.client.render.*;
import com.minecolonies.core.client.render.mobs.RenderMercenary;
import com.minecolonies.core.client.render.mobs.amazon.RendererAmazon;
import com.minecolonies.core.client.render.mobs.amazon.RendererAmazonSpearman;
import com.minecolonies.core.client.render.mobs.amazon.RendererChiefAmazon;
import com.minecolonies.core.client.render.mobs.barbarians.RendererBarbarian;
import com.minecolonies.core.client.render.mobs.barbarians.RendererChiefBarbarian;
import com.minecolonies.core.client.render.mobs.drownedpirates.RendererDrownedArcherPirate;
import com.minecolonies.core.client.render.mobs.drownedpirates.RendererDrownedChiefPirate;
import com.minecolonies.core.client.render.mobs.drownedpirates.RendererDrownedPirate;
import com.minecolonies.core.client.render.mobs.egyptians.RendererArcherMummy;
import com.minecolonies.core.client.render.mobs.egyptians.RendererMummy;
import com.minecolonies.core.client.render.mobs.egyptians.RendererPharao;
import com.minecolonies.core.client.render.mobs.norsemen.RendererArcherNorsemen;
import com.minecolonies.core.client.render.mobs.norsemen.RendererChiefNorsemen;
import com.minecolonies.core.client.render.mobs.norsemen.RendererShieldmaidenNorsemen;
import com.minecolonies.core.client.render.mobs.pirates.RendererArcherPirate;
import com.minecolonies.core.client.render.mobs.pirates.RendererChiefPirate;
import com.minecolonies.core.client.render.mobs.pirates.RendererPirate;
import com.minecolonies.core.client.render.projectile.FireArrowRenderer;
import com.minecolonies.core.client.render.projectile.RendererSpear;
import com.minecolonies.core.client.render.worldevent.ColonyBlueprintRenderer;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Client-side registration.
 * <p>
 * <b>Port note (contract C5).</b> The four {@code @SubscribeEvent} methods became plain {@code register*}
 * hooks driven by {@link #register()} from the client initializer, and each NeoForge event was replaced by
 * its Fabric registry: {@code EntityRenderersEvent.RegisterLayerDefinitions} by {@link ModelLayerRegistry},
 * {@code EntityRenderersEvent.RegisterRenderers} by {@link EntityRendererRegistry} /
 * {@link BlockEntityRendererRegistry}, {@code RegisterKeyMappingsEvent} by
 * {@code ModKeyMappings.register()}.
 */
@Environment(EnvType.CLIENT)
public class ClientRegistryHandler
{
    /**
     * Installs everything client-side. Called once from the client initializer.
     */
    public static void register()
    {
        registerLayerDefinitions();
        registerRenderers();
        ModKeyMappings.register();

        // was: RegisterItemDecorationsEvent, which bound a decorator to one item. Fabric's callback is global
        // instead, so each decorator filters on the stack itself; the drawn result is the same.
        ExtractItemDecorationsCallback.EVENT.register(new ColonyMapDecorator());
        ExtractItemDecorationsCallback.EVENT.register(new ClipBoardDecorator());

        // TODO(port-26.2): DISABLED (degradation ladder step 1) -- RegisterRecipeBookCategoriesEvent hid the
        // composting recipes from the vanilla recipe book. Fabric API has no equivalent; the only effect is
        // some "unknown recipe book category" log noise.
    }

    public static final ModelLayerLocation FEMALE_FARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_farmer"), "female_farmer");
    public static final ModelLayerLocation MALE_COURIER  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_deliveryman"), "male_deliveryman");
    public static final ModelLayerLocation FEMALE_CHILD  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_child"), "female_child");
    public static final ModelLayerLocation FEMALE_SHEEPFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_sheepfarmer"), "female_sheepfarmer");
    public static final ModelLayerLocation MALE_CHILD = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_child"), "male_child");
    public static final ModelLayerLocation FEMALE_CONCRETEMIXER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_concretemixer"), "female_concretemixer");
    public static final ModelLayerLocation MALE_COOK = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_cook"), "male_cook");
    public static final ModelLayerLocation MALE_SHEEPFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_sheepfarmer"), "male_sheepfarmer");
    public static final ModelLayerLocation MALE_SMELTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_smelter"), "male_smelter");
    public static final ModelLayerLocation MALE_UNDERTAKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_undertaker"), "male_undertaker");
    public static final ModelLayerLocation FEMALE_BUILDER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_builder"), "female_builder");
    public static final ModelLayerLocation MALE_BUILDER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_builder"), "male_builder");
    public static final ModelLayerLocation FEMALE_BAKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_baker"), "female_baker");
    public static final ModelLayerLocation MALE_MECHANIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_mechanist"), "male_mechanist");
    public static final ModelLayerLocation FEMALE_TEACHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_teacher"), "female_teacher");
    public static final ModelLayerLocation FEMALE_COMPOSTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_composter"), "female_composter");
    public static final ModelLayerLocation FEMALE_RABBITHERDER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_rabbitherder"), "female_rabbitherder");
    public static final ModelLayerLocation FEMALE_DYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_dyer"), "female_dyer");
    public static final ModelLayerLocation FEMALE_UNDERTAKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_undertaker"), "female_undertaker");
    public static final ModelLayerLocation MALE_COMPOSTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_composter"), "male_composter");
    public static final ModelLayerLocation MALE_FLETCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_fletcher"), "male_fletcher");
    public static final ModelLayerLocation MALE_CITIZEN    = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_citizen"), "male_citizen");
    public static final ModelLayerLocation FEMALE_CITIZEN    = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_citizen"), "female_citizen");
    public static final ModelLayerLocation FEMALE_SETTLER    = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_settler"), "female_settler");
    public static final ModelLayerLocation MALE_SETTLER    = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_settler"), "male_settler");
    public static final ModelLayerLocation FEMALE_FISHER     = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_fisherman"), "female_fisherman");
    public static final ModelLayerLocation MALE_RABBITHERDER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_rabbitherder"), "male_rabbitherder");
    public static final ModelLayerLocation FEMALE_FLETCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_fletcher"), "female_fletcher");
    public static final ModelLayerLocation FEMALE_CRAFTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_crafter"), "female_crafter");
    public static final ModelLayerLocation MALE_DYER          = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_dyer"), "male_dyer");
    public static final ModelLayerLocation MALE_FORESTER      = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_lumberjack"), "male_lumberjack");
    public static final ModelLayerLocation MALE_CONCRETEMIXER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_concretemixer"), "male_concretemixer");
    public static final ModelLayerLocation FEMALE_CHICKENFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_chickenfarmer"), "female_chickenfarmer");
    public static final ModelLayerLocation MALE_MINER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_miner"), "male_miner");
    public static final ModelLayerLocation FEMALE_MINER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_miner"), "female_miner");
    public static final ModelLayerLocation MALE_CHICKENFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_chickenfarmer"), "male_chickenfarmer");
    public static final ModelLayerLocation MALE_GLASSBLOWER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_glassblower"), "male_glassblower");
    public static final ModelLayerLocation FEMALE_PIGFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_pigfarmer"), "female_pigfarmer");
    public static final ModelLayerLocation FEMALE_CITIZENNOBLE = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_citizennoble"), "female_citizennoble");
    public static final ModelLayerLocation MALE_CITIZENNOBLE = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_citizennoble"), "male_citizennoble");
    public static final ModelLayerLocation MALE_BLACKSMITH = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_blacksmith"), "male_blacksmith");
    public static final ModelLayerLocation FEMALE_GLASSBLOWER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_glassblower"), "female_glassblower");
    public static final ModelLayerLocation FEMALE_BLACKSMITH = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_blacksmith"), "female_blacksmith");
    public static final ModelLayerLocation MALE_FARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_farmer"), "male_farmer");
    public static final ModelLayerLocation MALE_PIGFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_pigfarmer"), "male_pigfarmer");
    public static final ModelLayerLocation FEMALE_ARISTOCRAT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_aristocrat"), "female_aristocrat");
    public static final ModelLayerLocation MALE_ARISTOCRAT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_aristocrat"), "male_aristocrat");
    public static final ModelLayerLocation MALE_COWFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_cowfarmer"), "male_cowfarmer");
    public static final ModelLayerLocation FEMALE_SMELTER  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_smelter"), "female_smelter");
    public static final ModelLayerLocation FEMALE_FORESTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_lumberjack"), "female_lumberjack");
    public static final ModelLayerLocation FEMALE_COURIER  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_deliveryman"), "female_deliveryman");
    public static final ModelLayerLocation FEMALE_HEALER     = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_healer"), "female_healer");
    public static final ModelLayerLocation FEMALE_PLANTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_planter"), "female_planter");
    public static final ModelLayerLocation FEMALE_STUDENT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_student"), "female_student");
    public static final ModelLayerLocation FEMALE_COWFARMER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_cowfarmer"), "female_cowfarmer");
    public static final ModelLayerLocation FEMALE_MECHANIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_mechanist"), "female_mechanist");
    public static final ModelLayerLocation FEMALE_COOK  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_cook"), "female_cook");
    public static final ModelLayerLocation MALE_FISHER  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_fisherman"), "male_fisherman");
    public static final ModelLayerLocation MALE_PLANTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_planter"), "male_planter");
    public static final ModelLayerLocation MALE_BAKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_baker"), "male_baker");
    public static final ModelLayerLocation FEMALE_BEEKEEPER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_beekeeper"), "female_beekeeper");
    public static final ModelLayerLocation MALE_BEEKEEPER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_beekeeper"), "male_beekeeper");
    public static final ModelLayerLocation MALE_TEACHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_teacher"), "male_teacher");
    public static final ModelLayerLocation MALE_STUDENT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_student"), "male_student");
    public static final ModelLayerLocation MALE_HEALER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_healer"), "male_healer");
    public static final ModelLayerLocation MALE_CRAFTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_crafter"), "male_crafter");
    public static final ModelLayerLocation MALE_DRUID = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_druid"), "male_druid");
    public static final ModelLayerLocation FEMALE_DRUID = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_druid"), "female_druid");
    public static final ModelLayerLocation MALE_NETHERWORKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_netherworker"), "male_netherworker");
    public static final ModelLayerLocation FEMALE_NETHERWORKER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_netherworker"), "female_netherworker");
    public static final ModelLayerLocation MALE_ENCHANTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_enchanter"), "male_enchanter");
    public static final ModelLayerLocation FEMALE_ENCHANTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_enchanter"), "female_enchanter");
    public static final ModelLayerLocation MALE_FLORIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_florist"), "male_florist");
    public static final ModelLayerLocation FEMALE_FLORIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_florist"), "female_florist");
    public static final ModelLayerLocation MALE_KNIGHT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_knight"), "male_knight");
    public static final ModelLayerLocation FEMALE_KNIGHT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_knight"), "female_knight");
    public static final ModelLayerLocation MALE_ARCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_archer"), "male_archer");
    public static final ModelLayerLocation FEMALE_ARCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_archer"), "female_archer");
    public static final ModelLayerLocation FEMALE_CARPENTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_carpenter"), "female_carpenter");
    public static final ModelLayerLocation MALE_CARPENTER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_carpenter"), "male_carpenter");
    public static final ModelLayerLocation MALE_ALCHEMIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "male_alchemist"), "male_alchemist");
    public static final ModelLayerLocation FEMALE_ALCHEMIST = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "female_alchemist"), "female_alchemist");

    public static final ModelLayerLocation MERCENARY    = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mercenary"), "mercenary");

    public static final ModelLayerLocation MUMMY        = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mummy"), "mummy");
    public static final ModelLayerLocation ARCHER_MUMMY = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "archer_mummy"), "archer_mummy");
    public static final ModelLayerLocation PHARAO       = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pharao"), "pharao");

    public static final ModelLayerLocation SHIELD_MAIDEN   = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "shield_maiden"), "shield_maiden");
    public static final ModelLayerLocation NORSEMEN_ARCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "norsemen_archer"), "norsemen_archer");
    public static final ModelLayerLocation NORSEMEN_CHIEF  = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "norsemen_chief"), "norsemen_chief");

    public static final ModelLayerLocation AMAZON       = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazon"), "amazon");
    public static final ModelLayerLocation AMAZON_CHIEF = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazon_chief"), "amazon_chief");
    public static final ModelLayerLocation AMAZON_SPEARMAN = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazon_spearman"), "amazon_spearman");

    public static final ModelLayerLocation SCARECROW = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scarecrow"), "scarecrow");

    public static final ModelLayerLocation CITIZEN = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "citizen"), "citizen");

    private static void registerLayerDefinitions()
    {
        ModelLayerRegistry.registerModelLayer(MERCENARY, MercenaryModel::createMesh);

        ModelLayerRegistry.registerModelLayer(AMAZON, ModelAmazon::createMesh);
        ModelLayerRegistry.registerModelLayer(AMAZON_CHIEF, ModelAmazonChief::createMesh);
        ModelLayerRegistry.registerModelLayer(AMAZON_SPEARMAN, ModelAmazonSpearman::createMesh);

        ModelLayerRegistry.registerModelLayer(ARCHER_MUMMY, ModelArcherMummy::createMesh);
        ModelLayerRegistry.registerModelLayer(MUMMY, ModelMummy::createMesh);
        ModelLayerRegistry.registerModelLayer(PHARAO, ModelPharaoh::createMesh);

        ModelLayerRegistry.registerModelLayer(SHIELD_MAIDEN, ModelShieldmaiden::createMesh);
        ModelLayerRegistry.registerModelLayer(NORSEMEN_ARCHER, ModelArcherNorsemen::createMesh);
        ModelLayerRegistry.registerModelLayer(NORSEMEN_CHIEF, ModelChiefNorsemen::createMesh);

        ModelLayerRegistry.registerModelLayer(SCARECROW, ScarecrowModel::createMesh);

        ModelLayerRegistry.registerModelLayer(FEMALE_FARMER, FemaleFarmerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_COURIER, MaleCourierModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CHILD, FemaleChildModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_SHEEPFARMER, FemaleShepherdModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CHILD, MaleChildModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CONCRETEMIXER, FemaleConcreteMixerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_COOK, MaleCookModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_SHEEPFARMER, MaleShepherdModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_SMELTER, MaleSmelterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_UNDERTAKER, MaleUndertakerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_BUILDER, FemaleBuilderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_BUILDER, MaleBuilderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_BAKER, FemaleBakerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_MECHANIST, MaleMechanistModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_TEACHER, FemaleTeacherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_COMPOSTER, FemaleComposterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_RABBITHERDER, FemaleRabbitHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_DYER, FemaleDyerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_UNDERTAKER, FemaleUndertakerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_COMPOSTER, MaleComposterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_FLETCHER, MaleFletcherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CITIZEN, FemaleCitizenModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CITIZEN, MaleCitizenModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_SETTLER, FemaleSettlerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_SETTLER, MaleSettlerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_FISHER, FemaleFisherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_RABBITHERDER, MaleRabbitHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_FLETCHER, FemaleFletcherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CRAFTER, FemaleCrafterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_DYER, MaleDyerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_FORESTER, MaleForesterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CONCRETEMIXER, MaleConcreteMixerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CHICKENFARMER, FemaleChickenHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_MINER, MaleMinerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_MINER, FemaleMinerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CHICKENFARMER, MaleChickenHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_GLASSBLOWER, MaleGlassblowerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_PIGFARMER, FemaleSwineHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CITIZENNOBLE, FemaleNobleModle::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CITIZENNOBLE, MaleNobleModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_BLACKSMITH, MaleBlacksmithModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_GLASSBLOWER, FemaleGlassblowerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_BLACKSMITH, FemaleBlacksmithModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_FARMER, MaleFarmerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_PIGFARMER, MaleSwineHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_ARISTOCRAT, FemaleAristocratModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_ARISTOCRAT, MaleAristocratModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_COWFARMER, MaleCowHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_SMELTER, FemaleSmelterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_FORESTER, FemaleForesterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_COURIER, FemaleCourierModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_HEALER, FemaleHealerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_PLANTER, FemalePlanterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_STUDENT, FemaleStudentModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_COWFARMER, FemaleCowHerderModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_MECHANIST, FemaleMechanistModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_COOK, FemaleCookModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_FISHER, MaleFisherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_PLANTER, MalePlanterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_BAKER, MaleBakerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_BEEKEEPER, FemaleApiaryModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_BEEKEEPER, MaleApiaryModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_TEACHER, MaleTeacherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_STUDENT, MaleStudentModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_HEALER, MaleHealerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CRAFTER, MaleCrafterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_DRUID, MaleDruidModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_DRUID, FemaleDruidModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_NETHERWORKER, MaleNetherWorkerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_NETHERWORKER, FemaleNetherWorkerModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_FLORIST, MaleFloristModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_FLORIST, FemaleFloristModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_ENCHANTER, MaleEnchanterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_ENCHANTER, FemaleEnchanterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_KNIGHT, MaleKnightModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_KNIGHT, FemaleKnightModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_ARCHER, MaleArcherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_ARCHER, FemaleArcherModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_CARPENTER, MaleCarpenterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_CARPENTER, FemaleCarpenterModel::createMesh);
        ModelLayerRegistry.registerModelLayer(MALE_ALCHEMIST, MaleAlchemistModel::createMesh);
        ModelLayerRegistry.registerModelLayer(FEMALE_ALCHEMIST, FemaleAlchemistModel::createMesh);

        ModelLayerRegistry.registerModelLayer(CITIZEN, CitizenModel::createMesh);
    }

    private static void registerRenderers()
    {
        EntityRendererRegistry.register(ModEntities.CITIZEN, RenderBipedCitizen::new);
        EntityRendererRegistry.register(ModEntities.VISITOR, RenderBipedCitizen::new);
        EntityRendererRegistry.register(ModEntities.FISHHOOK, RenderFishHook::new);
        EntityRendererRegistry.register(ModEntities.FIREARROW, FireArrowRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPEAR, RendererSpear::new);

        EntityRendererRegistry.register(ModEntities.MC_NORMAL_ARROW, TippableArrowRenderer::new);
        EntityRendererRegistry.register(ModEntities.DRUID_POTION, m -> new ThrownItemRenderer<>(m, 1.0F, true));

        // Raiders

        EntityRendererRegistry.register(ModEntities.BARBARIAN, RendererBarbarian::new);
        EntityRendererRegistry.register(ModEntities.ARCHERBARBARIAN, RendererBarbarian::new);
        EntityRendererRegistry.register(ModEntities.CHIEFBARBARIAN, RendererChiefBarbarian::new);

        EntityRendererRegistry.register(ModEntities.PIRATE, RendererPirate::new);
        EntityRendererRegistry.register(ModEntities.ARCHERPIRATE, RendererArcherPirate::new);
        EntityRendererRegistry.register(ModEntities.CHIEFPIRATE, RendererChiefPirate::new);

        EntityRendererRegistry.register(ModEntities.MUMMY, RendererMummy::new);
        EntityRendererRegistry.register(ModEntities.ARCHERMUMMY, RendererArcherMummy::new);
        EntityRendererRegistry.register(ModEntities.PHARAO, RendererPharao::new);

        EntityRendererRegistry.register(ModEntities.SHIELDMAIDEN, RendererShieldmaidenNorsemen::new);
        EntityRendererRegistry.register(ModEntities.NORSEMEN_ARCHER, RendererArcherNorsemen::new);
        EntityRendererRegistry.register(ModEntities.NORSEMEN_CHIEF, RendererChiefNorsemen::new);

        EntityRendererRegistry.register(ModEntities.AMAZON, RendererAmazon::new);
        EntityRendererRegistry.register(ModEntities.AMAZONCHIEF, RendererChiefAmazon::new);
        EntityRendererRegistry.register(ModEntities.AMAZONSPEARMAN, RendererAmazonSpearman::new);

        EntityRendererRegistry.register(ModEntities.DROWNED_PIRATE, RendererDrownedPirate::new);
        EntityRendererRegistry.register(ModEntities.DROWNED_ARCHERPIRATE, RendererDrownedArcherPirate::new);
        EntityRendererRegistry.register(ModEntities.DROWNED_CHIEFPIRATE, RendererDrownedChiefPirate::new);

        // Camp Raiders

        EntityRendererRegistry.register(ModEntities.CAMP_BARBARIAN, RendererBarbarian::new);
        EntityRendererRegistry.register(ModEntities.CAMP_ARCHERBARBARIAN, RendererBarbarian::new);
        EntityRendererRegistry.register(ModEntities.CAMP_CHIEFBARBARIAN, RendererChiefBarbarian::new);

        EntityRendererRegistry.register(ModEntities.CAMP_PIRATE, RendererPirate::new);
        EntityRendererRegistry.register(ModEntities.CAMP_ARCHERPIRATE, RendererArcherPirate::new);
        EntityRendererRegistry.register(ModEntities.CAMP_CHIEFPIRATE, RendererChiefPirate::new);

        EntityRendererRegistry.register(ModEntities.CAMP_MUMMY, RendererMummy::new);
        EntityRendererRegistry.register(ModEntities.CAMP_ARCHERMUMMY, RendererArcherMummy::new);
        EntityRendererRegistry.register(ModEntities.CAMP_PHARAO, RendererPharao::new);

        EntityRendererRegistry.register(ModEntities.CAMP_SHIELDMAIDEN, RendererShieldmaidenNorsemen::new);
        EntityRendererRegistry.register(ModEntities.CAMP_NORSEMEN_ARCHER, RendererArcherNorsemen::new);
        EntityRendererRegistry.register(ModEntities.CAMP_NORSEMEN_CHIEF, RendererChiefNorsemen::new);

        EntityRendererRegistry.register(ModEntities.CAMP_AMAZON, RendererAmazon::new);
        EntityRendererRegistry.register(ModEntities.CAMP_AMAZONCHIEF, RendererChiefAmazon::new);
        EntityRendererRegistry.register(ModEntities.CAMP_AMAZONSPEARMAN, RendererAmazonSpearman::new);

        EntityRendererRegistry.register(ModEntities.CAMP_DROWNED_PIRATE, RendererDrownedPirate::new);
        EntityRendererRegistry.register(ModEntities.CAMP_DROWNED_ARCHERPIRATE, RendererDrownedArcherPirate::new);
        EntityRendererRegistry.register(ModEntities.CAMP_DROWNED_CHIEFPIRATE, RendererDrownedChiefPirate::new);

        // Misc

        EntityRendererRegistry.register(ModEntities.MERCENARY, RenderMercenary::new);
        EntityRendererRegistry.register(ModEntities.SITTINGENTITY, RenderSitting::new);
        EntityRendererRegistry.register(ModEntities.MINECART, (context) -> new MinecartRenderer(context, ModelLayers.MINECART));
        // Vanilla's BoatRenderer derives both model and texture from the layer it is handed, so an oak boat costs us
        // no assets of our own.
        EntityRendererRegistry.register(ModEntities.BOAT, (context) -> new BoatRenderer(context, ModelLayers.OAK_BOAT));
        // Port note: the overlay cannot go on through Fabric's layer-registration callback. That callback is
        // invariant in the render state -- HorseRenderer's model is an EntityModel<EquineRenderState> while a
        // layer registered against it is a RenderLayer<HorseRenderState, ...>, so RegistrationHelper#register
        // rejects even vanilla's own HorseMarkingLayer. HorseRenderer is also final in 26.2, hence the mod's own
        // CavalryHorseRenderer (zone D), which mirrors the vanilla renderer, adds CavalryOverlayLayer itself,
        // and is the only place that can read combat readiness off the entity -- layers no longer see it, so the
        // value travels on the render state.
        EntityRendererRegistry.register(ModEntities.CAVALRY_HORSE, CavalryHorseRenderer::new);

        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.BUILDING.get(), EmptyTileEntitySpecialRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.SCARECROW.get(), TileEntityScarecrowRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.ENCHANTER.get(), TileEntityEnchanterRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.COLONY_FLAG.get(), TileEntityColonyFlagRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.NAMED_GRAVE.get(), TileEntityNamedGraveRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.DECO_CONTROLLER.get(), TileEntityDecoControllerRenderer::new);
        BlockEntityRendererRegistry.register(MinecoloniesTileEntities.COLONY_SIGN.get(), TileEntityColonySignRenderer::new);

        // TODO(port-26.2): DISABLED (degradation ladder step 1) -- ItemBlockRenderTypes and ItemProperties are
        // both gone in 26.2: the render layer of a block is declared by its model json ("render_type") and the
        // "throwing" / "disabled" item predicates became item model definitions under
        // assets/minecolonies/items/. Datagen has to emit both; until it does, the huts, scarecrow, rack,
        // decoration controller, composted dirt, barrel, waypoint, flooded farmland and every crop render on
        // the solid layer, and the spear/goggles never switch to their alternate model.
    }

}

package top.girlkisser.cygnus.client.skybox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.girlkisser.cygnus.api.mathematics.QuaternionHelpers;
import top.girlkisser.cygnus.mixin.client.LevelRendererAccessor;
import top.girlkisser.lazuli.api.colour.UnpackedColour;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class AbstractSpaceSpecialEffects extends DimensionSpecialEffects
{
	public static final long STAR_SEED = 10842L;

	protected final Minecraft minecraft = Minecraft.getInstance();

	public AbstractSpaceSpecialEffects()
	{
		super(Float.NaN, true, SkyType.NONE, false, false);
	}

	protected abstract List<SkyboxObject> getSkyboxObjects();
	protected abstract @Nullable VertexBuffer getStarBuffer();
	protected abstract boolean useOrbitSkyboxObjects();
	protected abstract boolean usePlanetSkyboxObjects();

	protected float getStarBrightness(float partialTick)
	{
		var level = level();
		float rainLevel = 1.0F - level.getRainLevel(partialTick);
		return level.getStarBrightness(partialTick) * rainLevel;
	}

	protected ClientLevel level()
	{
		assert this.minecraft.level != null;
		return this.minecraft.level;
	}

	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness)
	{
		return fogColor;
	}

	@Override
	public boolean isFoggyAt(int x, int y)
	{
		return false;
	}

	@Override
	public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
	{
		return true;
	}

	@SuppressWarnings("resource")
	@Override
	public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f frustumMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
	{
		setupFog.run();
		if (isFoggy)
			return true;

		LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
		LevelRendererAccessor lra = (LevelRendererAccessor) renderer;

		FogType fogType = camera.getFluidInCamera();
		if (fogType == FogType.POWDER_SNOW || fogType == FogType.LAVA || lra.cygnus$doesMobEffectBlockSky(camera))
			return true;

		LocalPlayer player = minecraft.player;
		assert player != null;
		Tesselator tes = Tesselator.getInstance();

		PoseStack poses = new PoseStack();
		poses.mulPose(frustumMatrix);

		renderSkybox(poses, level, partialTick, projectionMatrix);

		float rainLevel = 1.0F - level.getRainLevel(partialTick);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, rainLevel);

		// Render stars
		if (getStarBuffer() != null)
		{
			float starBrightness = getStarBrightness(partialTick);
			if (starBrightness > 0)
			{
				RenderSystem.setShaderColor(starBrightness, starBrightness, starBrightness, starBrightness);
				FogRenderer.setupNoFog();
				getStarBuffer().bind();
				//noinspection DataFlowIssue
				getStarBuffer().drawWithShader(poses.last().pose(), projectionMatrix, GameRenderer.getPositionColorShader());
				VertexBuffer.unbind();
				setupFog.run();
			}
		}

		// Render skybox objects
		// This line changes black pixels into the sky colour
//		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		poses.pushPose();

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		boolean useOrbitObjects = useOrbitSkyboxObjects();
		boolean usePlanetObjects = usePlanetSkyboxObjects();
		for (SkyboxObject obj : getSkyboxObjects())
		{
			if (obj.isForOrbit() && !useOrbitObjects)
				continue;
			if (obj.isForPlanet() && !usePlanetObjects)
				continue;
			renderSkyboxObject(obj, poses, level, partialTick, tes);
		}

		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		poses.popPose();

		// I honestly have no clue what this part of the sky rendering does, I think it's the horizon?
		RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
		double d0 = player.getEyePosition(partialTick).y - level.getLevelData().getHorizonHeight(level);
		if (d0 < 0d)
		{
			poses.pushPose();
			poses.translate(0.0F, 12.0F, 0.0F);
			lra.cygnus$getDarkBuffer().bind();
			//noinspection DataFlowIssue
			lra.cygnus$getDarkBuffer().drawWithShader(poses.last().pose(), projectionMatrix, RenderSystem.getShader());
			VertexBuffer.unbind();
			poses.popPose();
		}

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.depthMask(true);

		return true;
	}

//	@Override
//	public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ)
//	{
//		return false;
//	}

	@SuppressWarnings("resource")
	protected void renderSkybox(PoseStack poses, ClientLevel level, float partialTick, Matrix4f projectionMatrix)
	{
		LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
		LevelRendererAccessor lra = (LevelRendererAccessor) renderer;
		Tesselator tesselator = Tesselator.getInstance();

		Vec3 skyColor = level.getSkyColor(this.minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
		float skyR = (float) skyColor.x;
		float skyG = (float) skyColor.y;
		float skyB = (float) skyColor.z;
		FogRenderer.levelFogColor();
		RenderSystem.depthMask(false);
		RenderSystem.setShaderColor(skyR, skyG, skyB, 1.0F);
		lra.cygnus$getSkyBuffer().bind();
		//noinspection DataFlowIssue
		lra.cygnus$getSkyBuffer().drawWithShader(poses.last().pose(), projectionMatrix, RenderSystem.getShader());
		VertexBuffer.unbind();
		RenderSystem.enableBlend();
		float[] sunriseColor = getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
		if (sunriseColor != null)
		{
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			poses.pushPose();
			poses.mulPose(Axis.XP.rotationDegrees(90.0F));
			float f3 = Mth.sin(level.getSunAngle(partialTick)) < 0.0F ? 180.0F : 0.0F;
			poses.mulPose(Axis.ZP.rotationDegrees(f3));
			poses.mulPose(Axis.ZP.rotationDegrees(90.0F));
			Matrix4f matrix4f = poses.last().pose();
			BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
			buffer.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(sunriseColor[0], sunriseColor[1], sunriseColor[2], sunriseColor[3]);

			for (int i = 0 ; i <= 16 ; i++)
			{
				float f7 = (float) i * ((float) Math.PI * 2F) / 16.0F;
				float f8 = Mth.sin(f7);
				float f9 = Mth.cos(f7);
				buffer.addVertex(matrix4f, f8 * 120.0F, f9 * 120.0F, -f9 * 40.0F * sunriseColor[3]).setColor(sunriseColor[0], sunriseColor[1], sunriseColor[2], 0.0F);
			}

			BufferUploader.drawWithShader(buffer.buildOrThrow());
			poses.popPose();
		}
	}

	protected void renderSkyboxObject(SkyboxObject object, PoseStack poses, ClientLevel level, float partialTick, Tesselator tes)
	{
		poses.pushPose();

		poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
		for (SkyboxTransform transform : object.transforms())
			transform.apply(object, poses, level, partialTick);

		// Render the backlight (if there is one)
		if (object.backlight().isPresent())
		{
			SkyboxObject.Backlight backlight = object.backlight().get();
			float size = backlight.size() * object.scale();
			int colour = backlight.color().pack();

			poses.pushPose();
			for (SkyboxTransform transform : backlight.transforms())
				transform.apply(object, poses, level, partialTick);

			poses.pushTransformation(new Transformation(
				new Vector3f(0, object.distance() + backlight.distance(), 0),
				QuaternionHelpers.castDoublesToFloats(QuaternionHelpers.fromRollPitchYawDegrees(
					object.textureRotation().x + backlight.textureRotation().x,
					object.textureRotation().y + backlight.textureRotation().y,
					object.textureRotation().z + backlight.textureRotation().z
				)),
				null,
				null
			));

			PoseStack.Pose pose = poses.last();
			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
			RenderSystem.setShaderTexture(0, backlight.texture());
			BufferBuilder buffer = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			buffer.addVertex(pose, new Vector3f(-size, 0, -size)).setUv(0.0F, 0.0F).setColor(colour);
			buffer.addVertex(pose, new Vector3f(size, 0, -size)).setUv(1.0F, 0.0F).setColor(colour);
			buffer.addVertex(pose, new Vector3f(size, 0, size)).setUv(1.0F, 1.0F).setColor(colour);
			buffer.addVertex(pose, new Vector3f(-size, 0, size)).setUv(0.0F, 1.0F).setColor(colour);
			BufferUploader.drawWithShader(buffer.buildOrThrow());

			poses.popPose();
			poses.popPose();
		}

		for (SkyboxObject.Layer layer : object.layers())
		{
			float size = layer.size() * object.scale();

			poses.pushPose();
			for (SkyboxTransform transform : layer.transforms())
				transform.apply(object, poses, level, partialTick);

			poses.pushTransformation(new Transformation(
				new Vector3f(0, object.distance() + layer.distance(), 0),
				QuaternionHelpers.castDoublesToFloats(QuaternionHelpers.fromRollPitchYawDegrees(
					object.textureRotation().x + layer.textureRotation().x,
					object.textureRotation().y + layer.textureRotation().y,
					object.textureRotation().z + layer.textureRotation().z
				)),
				null,
				null
			));

			PoseStack.Pose pose = poses.last();
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderTexture(0, layer.texture());
			BufferBuilder buffer = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			buffer.addVertex(pose, new Vector3f(-size, 0, -size)).setUv(0.0F, 0.0F);
			buffer.addVertex(pose, new Vector3f(size, 0, -size)).setUv(1.0F, 0.0F);
			buffer.addVertex(pose, new Vector3f(size, 0, size)).setUv(1.0F, 1.0F);
			buffer.addVertex(pose, new Vector3f(-size, 0, size)).setUv(0.0F, 1.0F);
			BufferUploader.drawWithShader(buffer.buildOrThrow());

			poses.popPose();
			poses.popPose();
		}

		poses.popPose();
	}

	protected MeshData makeStars(PlanetRenderer.StarInfo stars, Tesselator tes)
	{
		RandomSource rand = RandomSource.create(STAR_SEED);
		BufferBuilder buffer = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

		for (int i = 0 ; i < stars.count() ; i++)
		{
			float x = rand.nextFloat() * 2.0F - 1.0F;
			float y = rand.nextFloat() * 2.0F - 1.0F;
			float z = rand.nextFloat() * 2.0F - 1.0F;
			float f5 = Mth.lengthSquared(x, y, z);
			if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F))
			{
				PlanetRenderer.StarInfo.StarVariant variant = stars.pickVariant(rand);
				UnpackedColour colour = variant.color();

				float size = variant.size() + rand.nextFloat() * stars.sizeVariance();

				Vector3f pos = new Vector3f(x, y, z).normalize(100.0F);
				float rotation = (float) (rand.nextDouble() * Math.PI * 2d);
				Quaternionf quaternionf = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), pos).rotateZ(rotation);
				buffer.addVertex(pos.add((new Vector3f(size, -size, 0.0F)).rotate(quaternionf))).setColor(colour.r(), colour.g(), colour.b(), colour.a());
				buffer.addVertex(pos.add((new Vector3f(size, size, 0.0F)).rotate(quaternionf))).setColor(colour.r(), colour.g(), colour.b(), colour.a());
				buffer.addVertex(pos.add((new Vector3f(-size, size, 0.0F)).rotate(quaternionf))).setColor(colour.r(), colour.g(), colour.b(), colour.a());
				buffer.addVertex(pos.add((new Vector3f(-size, -size, 0.0F)).rotate(quaternionf))).setColor(colour.r(), colour.g(), colour.b(), colour.a());
			}
		}

		return buffer.buildOrThrow();
	}
}

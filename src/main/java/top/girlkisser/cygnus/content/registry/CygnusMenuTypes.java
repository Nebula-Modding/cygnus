package top.girlkisser.cygnus.content.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.ApiStatus;
import top.girlkisser.cygnus.Cygnus;
import top.girlkisser.cygnus.api.space.SpaceStation;
import top.girlkisser.cygnus.content.menu.ContainerChroniteBlastFurnace;
import top.girlkisser.cygnus.content.menu.ContainerCommandCentre;
import top.girlkisser.cygnus.content.menu.ContainerTerminal;

@SuppressWarnings("unused")
@ApiStatus.NonExtendable
public interface CygnusMenuTypes
{
	DeferredRegister<MenuType<?>> R = DeferredRegister.create(Registries.MENU, Cygnus.MODID);

	static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> reg(String id, IContainerFactory<T> factory)
	{
		return R.register(id, () -> IMenuTypeExtension.create(factory));
	}

	static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> regSimple(String id, TriFunction<Integer, Inventory, BlockPos, T> factory)
	{
		return reg(id, (windowId, inventory, buf) -> factory.apply(windowId, inventory, buf.readBlockPos()));
	}

	DeferredHolder<MenuType<?>, MenuType<ContainerTerminal>> TERMINAL = reg("terminal", (windowId, inventory, buf) ->
		new ContainerTerminal(windowId, inventory, buf.readBlockPos(), buf.readBoolean() ? SpaceStation.STREAM_CODEC.decode(buf) : null));

	DeferredHolder<MenuType<?>, MenuType<ContainerCommandCentre>> COMMAND_CENTRE = reg("command_centre", (windowId, inventory, buf) ->
		new ContainerCommandCentre(windowId, inventory, buf.readBlockPos(), buf.readBoolean() ? SpaceStation.STREAM_CODEC.decode(buf) : null));

	DeferredHolder<MenuType<?>, MenuType<ContainerChroniteBlastFurnace>> CHRONITE_BLAST_FURNACE = reg("chronite_blast_function", (windowId, inventory, buf) ->
		new ContainerChroniteBlastFurnace(windowId, inventory, buf.readBlockPos()));
}

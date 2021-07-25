package wile.anthillinside.libmc.ui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;
import java.util.function.Function;


public class Guis
{
  // -------------------------------------------------------------------------------------------------------------------
  // Gui base
  // -------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static abstract class ContainerGui<T extends Container> extends ContainerScreen<T> implements IHasContainer<T>
  {
    protected final ResourceLocation background_image;

    public ContainerGui(T screenContainer, PlayerInventory inv, ITextComponent title, ResourceLocation background_image)
    {
      super(screenContainer, inv, title);
      this.background_image = background_image;
    }

    protected boolean canHaveDisturbingButtonsFromOtherMods()
    { return false; }

    public void init(Minecraft minecraft, int width, int height)
    {
      super.init(minecraft, width, height);
      this.minecraft = minecraft;
      this.itemRenderer = minecraft.getItemRenderer();
      this.font = minecraft.font;
      this.width = width;
      this.height = height;
      java.util.function.Consumer<Widget> remove = (b) -> { buttons.remove(b); children.remove(b); };
      if((!canHaveDisturbingButtonsFromOtherMods()) || (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Pre(this, this.buttons, this::addButton, remove)))) {
        this.buttons.clear();
        this.children.clear();
        this.setFocused((IGuiEventListener)null);
        this.init();
      }
      if(canHaveDisturbingButtonsFromOtherMods()) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post(this, this.buttons, this::addButton, remove));
      }
    }

    @SuppressWarnings("deprecation")
    protected void renderItemTemplate(MatrixStack mx, ItemStack stack, int x, int y)
    {
      final ItemRenderer ir = itemRenderer;
      final int main_zl = getBlitOffset();
      final float zl = ir.blitOffset;
      final int x0 = getGuiLeft();
      final int y0 = getGuiTop();
      ir.blitOffset = -80;
      RenderSystem.enableRescaleNormal();
      ir.renderGuiItem(stack, x0+x, y0+y);
      RenderSystem.disableRescaleNormal();
      RenderSystem.disableLighting();
      RenderSystem.disableColorMaterial();
      RenderSystem.enableAlphaTest();
      RenderSystem.defaultAlphaFunc();
      RenderSystem.enableBlend();
      ir.blitOffset = zl;
      setBlitOffset(100);
      RenderSystem.colorMask(true, true, true, true);
      RenderSystem.color4f(0.7f, 0.7f, 0.7f, 0.8f);
      getMinecraft().getTextureManager().bind(background_image);
      blit(mx, x0+x, y0+y, x, y, 16, 16);
      RenderSystem.color4f(1f, 1f, 1f, 1f);
      setBlitOffset(main_zl);
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Gui elements
  // -------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class Coord2d
  {
    public final int x, y;
    public Coord2d(int x, int y) { this.x=x; this.y=y; }
  }

  @OnlyIn(Dist.CLIENT)
  public static class UiWidget extends net.minecraft.client.gui.widget.Widget
  {
    protected static final ITextComponent EMPTY_TEXT = new StringTextComponent("");
    protected static final Function<UiWidget,ITextComponent> NO_TOOLTIP = (uiw)->EMPTY_TEXT;

    private Function<UiWidget,ITextComponent> tooltip_ = NO_TOOLTIP;

    public UiWidget(int x, int y, int width, int height, ITextComponent title)
    { super(x, y, width, height, title); }

    public UiWidget init(Screen parent)
    {
      this.x += ((parent instanceof ContainerScreen<?>) ? ((ContainerScreen<?>)parent).getGuiLeft() : 0);
      this.y += ((parent instanceof ContainerScreen<?>) ? ((ContainerScreen<?>)parent).getGuiTop() : 0);
      return this;
    }

    public UiWidget init(Screen parent, Coord2d position)
    {
      this.x = position.x + ((parent instanceof ContainerScreen<?>) ? ((ContainerScreen<?>)parent).getGuiLeft() : 0);
      this.y = position.y + ((parent instanceof ContainerScreen<?>) ? ((ContainerScreen<?>)parent).getGuiTop() : 0);
      return this;
    }

    public int getWidth()
    { return this.width; }

    public int getHeight()
    { return this.height; }

    public UiWidget show()
    { visible = true; return this; }

    public UiWidget hide()
    { visible = false; return this; }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
    {
      super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
      if(isHovered()) renderToolTip(matrixStack, mouseX, mouseY);
    }

    @Override
    public void renderToolTip(MatrixStack matrixStack, int mouseX, int mouseY)
    {
      if(tooltip_ == NO_TOOLTIP) return;
      /// todo: need a Screen for that, not sure if adding a reference initialized in init() may cause GC problems.
    }

  }

  @OnlyIn(Dist.CLIENT)
  public static class HorizontalProgressBar extends UiWidget
  {
    private final Coord2d texture_position_base_;
    private final Coord2d texture_position_filled_;
    private final ResourceLocation atlas_;
    private double progress_max_ = 100;
    private double progress_ = 0;

    public HorizontalProgressBar(ResourceLocation atlas, int width, int height, Coord2d base_texture_xy, Coord2d filled_texture_xy)
    {
      super(0, 0, width, height, EMPTY_TEXT);
      atlas_ = atlas;
      texture_position_base_ = base_texture_xy;
      texture_position_filled_ = filled_texture_xy;
    }

    public HorizontalProgressBar setProgress(double progress)
    { progress_ = MathHelper.clamp(progress, 0, progress_max_); return this; }

    public double getProgress()
    { return progress_; }

    public HorizontalProgressBar setMaxProgress(double progress)
    { progress_max_ = Math.max(progress, 0); return this; }

    public double getMaxProgress()
    { return progress_max_; }

    public HorizontalProgressBar show()
    { visible = true; return this; }

    public HorizontalProgressBar hide()
    { visible = false; return this; }

    @Override
    public void playDownSound(SoundHandler handler)
    {}

    @Override
    protected void renderBg(MatrixStack mx, Minecraft mc, int x, int y)
    {}

    @Override
    @SuppressWarnings("deprecation")
    public void renderButton(MatrixStack mx, int mouseX, int mouseY, float partialTicks)
    {
      final Minecraft mc = Minecraft.getInstance();
      final FontRenderer fontrenderer = mc.font;
      mc.getTextureManager().bind(atlas_);
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      blit(mx, x, y, texture_position_base_.x, texture_position_base_.y, width, height);
      if((progress_max_ > 0) && (progress_ > 0)) {
        int w = MathHelper.clamp((int)Math.round((progress_ * width) / progress_max_), 0, width);
        blit(mx, x, y, texture_position_filled_.x, texture_position_filled_.y, w, height);
      }
    }
  }

  @OnlyIn(Dist.CLIENT)
  public static class BackgroundImage extends UiWidget
  {
    private final ResourceLocation atlas_;
    private final Coord2d atlas_position_;
    public boolean visible;

    public BackgroundImage(ResourceLocation atlas, int width, int height, Coord2d atlas_position)
    {
      super(0, 0, width, height, EMPTY_TEXT);
      atlas_ = atlas;
      atlas_position_ = atlas_position;
      this.width = width;
      this.height = height;
      visible = true;
    }

    public void draw(MatrixStack mx, Screen parent)
    {
      if(!visible) return;
      parent.getMinecraft().getTextureManager().bind(atlas_);
      parent.blit(mx, x, y, atlas_position_.x, atlas_position_.y, width, height);
    }

  }

  @OnlyIn(Dist.CLIENT)
  public static class CheckBox extends UiWidget
  {
    private final Coord2d texture_position_off_;
    private final Coord2d texture_position_on_;
    private final ResourceLocation atlas_;
    private boolean checked_ = false;
    private Consumer<CheckBox> on_click_ = (checkbox)->{};

    public CheckBox(ResourceLocation atlas, int width, int height, Coord2d atlas_texture_position_off, Coord2d atlas_texture_position_on)
    {
      super(0, 0, width, height, EMPTY_TEXT);
      texture_position_off_ = atlas_texture_position_off;
      texture_position_on_ = atlas_texture_position_on;
      atlas_ = atlas;
    }

    public boolean checked()
    { return checked_; }

    public CheckBox checked(boolean on)
    { checked_ = on; return this; }

    public CheckBox onclick(Consumer<CheckBox> action)
    { on_click_ = action; return this; }

    @Override
    public void onClick(double mouseX, double mouseY)
    { checked_ = !checked_; on_click_.accept(this); }

    @Override
    @SuppressWarnings("deprecation")
    public void renderButton(MatrixStack mx, int mouseX, int mouseY, float partialTicks)
    {
      final Minecraft mc = Minecraft.getInstance();
      final FontRenderer fontrenderer = mc.font;
      mc.getTextureManager().bind(atlas_);
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      Coord2d pos = checked_ ? texture_position_on_ : texture_position_off_;
      blit(mx, x, y, pos.x, pos.y, width, height);
    }

  }

}

package su.nightexpress.nexshop.shop.virtual.menu;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.api.menu.MenuItemType;
import su.nexmedia.engine.api.menu.click.ClickHandler;
import su.nexmedia.engine.api.menu.impl.ConfigMenu;
import su.nexmedia.engine.api.menu.impl.MenuOptions;
import su.nexmedia.engine.api.menu.impl.MenuViewer;
import su.nexmedia.engine.api.menu.item.ItemOptions;
import su.nexmedia.engine.api.menu.item.MenuItem;
import su.nexmedia.engine.api.placeholder.PlaceholderMap;
import su.nexmedia.engine.utils.*;
import su.nightexpress.nexshop.ExcellentShop;
import su.nightexpress.nexshop.api.shop.VirtualShop;
import su.nightexpress.nexshop.api.shop.product.VirtualProduct;
import su.nightexpress.nexshop.api.shop.type.ShopClickAction;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.config.Config;
import su.nightexpress.nexshop.config.Lang;
import su.nightexpress.nexshop.data.object.RotationData;
import su.nightexpress.nexshop.shop.virtual.VirtualShopModule;
import su.nightexpress.nexshop.shop.virtual.config.VirtualConfig;
import su.nightexpress.nexshop.shop.virtual.impl.RotatingProduct;
import su.nightexpress.nexshop.shop.virtual.impl.StaticProduct;
import su.nightexpress.nexshop.shop.virtual.impl.RotatingShop;
import su.nightexpress.nexshop.shop.virtual.impl.StaticShop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ShopMenu extends ConfigMenu<ExcellentShop> {

    private final VirtualShop shop;

    public ShopMenu(@NotNull ExcellentShop plugin, @NotNull VirtualShop shop, @NotNull JYML cfg) {
        super(plugin, cfg);
        this.shop = shop;

        cfg.addMissing("Title", StringUtil.capitalizeUnderscored(shop.getId()));
        cfg.addMissing("Size", 54);
        cfg.saveChanges();

        this.registerHandler(MenuItemType.class)
            .addClick(MenuItemType.PAGE_NEXT, ClickHandler.forNextPage(this))
            .addClick(MenuItemType.PAGE_PREVIOUS, ClickHandler.forPreviousPage(this))
            .addClick(MenuItemType.CLOSE, ClickHandler.forClose(this))
            .addClick(MenuItemType.RETURN, (viewer, event) -> {
                VirtualShopModule module = this.plugin.getVirtualShop();
                MainMenu menu = module == null ? null : module.getMainMenu();
                if (menu == null) {
                    viewer.getPlayer().closeInventory();
                    return;
                }
                menu.openNextTick(viewer.getPlayer(), 1);
            });

        this.reload();
    }

    @NotNull
    public JYML getConfig() {
        return this.cfg;
    }

    public void reload() {
        this.getItems().clear();

        this.load();

        this.getItems().forEach(menuItem -> menuItem.getOptions().addDisplayModifier((viewer, item) -> {
            ItemReplacer.create(item).readMeta().trimmed()
                .replace(this.shop.getPlaceholders())
                .replacePlaceholderAPI(viewer.getPlayer())
                .writeMeta();
        }));
    }

    @Override
    public void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        super.onPrepare(viewer, options);

        options.setTitle(this.shop.replacePlaceholders().apply(options.getTitle()));
        if (EngineUtils.hasPlaceholderAPI()) {
            options.setTitle(PlaceholderAPI.setPlaceholders(viewer.getPlayer(), options.getTitle()));
        }

        if (this.shop instanceof StaticShop staticShop) {
            this.displayStatic(staticShop, viewer, options);
        }
        else if (this.shop instanceof RotatingShop rotatingShop) {
            this.displayRotating(rotatingShop, viewer, options);
        }
    }

    private void displayStatic(@NotNull StaticShop shop, @NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        viewer.setPages(shop.getPages());
        viewer.finePage();

        int page = viewer.getPage();
        Player player = viewer.getPlayer();

        for (StaticProduct product : shop.getProducts()) {
            if (product.getPage() != page) continue;

            this.addProductItem(player, product, product.getSlot());
        }
    }

    private void displayRotating(@NotNull RotatingShop shop, @NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        RotationData data = shop.getData();
        Player player = viewer.getPlayer();
        Set<String> products = data.getProducts();
        int[] slots = shop.getProductSlots();

        int limit = slots.length;
        int pages = (int) Math.ceil((double) products.size() / (double) limit);

        viewer.setPages(pages);
        viewer.finePage();

        int skip = (viewer.getPage() - 1) * limit;
        List<String> list = new ArrayList<>(products.stream().skip(skip).limit(limit).toList());

        int count = 0;
        for (String prodId : list) {
            RotatingProduct product = shop.getProductById(prodId);
            if (product == null) continue;

            int slot = slots[count++];
            this.addProductItem(player, product, slot);
        }
    }

    private void addProductItem(@NotNull Player player, @NotNull VirtualProduct product, int slot) {
        ItemStack preview = product.getPreview();
        ItemUtil.mapMeta(preview, meta -> {
            List<String> loreFormat = VirtualConfig.PRODUCT_FORMAT_LORE_GENERAL_ALL.get();
            if (!product.isBuyable() || !shop.isTransactionEnabled(TradeType.BUY)) {
                loreFormat = VirtualConfig.PRODUCT_FORMAT_LORE_GENERAL_SELL_ONLY.get();
            }
            if (!product.isSellable() || !shop.isTransactionEnabled(TradeType.SELL)) {
                loreFormat = VirtualConfig.PRODUCT_FORMAT_LORE_GENERAL_BUY_ONLY.get();
            }

            List<String> lore = new ArrayList<>();

            Label_Format:
            for (String lineFormat : loreFormat) {
                if (lineFormat.equalsIgnoreCase("%lore%")) {
                    List<String> list2 = meta.getLore();
                    if (list2 != null) lore.addAll(list2);
                    continue;
                }
                else if (lineFormat.equalsIgnoreCase("%discount%")) {
                    if (this.shop.hasDiscount(product)) {
                        lore.addAll(VirtualConfig.PRODUCT_FORMAT_LORE_DISCOUNT.get());
                    }
                    continue;
                }
                else if (lineFormat.equalsIgnoreCase("%permission%")) {
                    if (!product.hasAccess(player)) {
                        lore.addAll(VirtualConfig.PRODUCT_FORMAT_LORE_NO_PERMISSION.get());
                    }
                    continue;
                }
                for (TradeType tradeType : TradeType.values()) {
                    if (lineFormat.equalsIgnoreCase("%stock_global_" + tradeType.name() + "%")) {
                        if (!product.getStockValues().isUnlimited(tradeType)) {
                            lore.addAll(VirtualConfig.PRODUCT_FORMAT_LORE_STOCK.get().getOrDefault(tradeType, Collections.emptyList()));
                        }
                        continue Label_Format;
                    }
                    if (lineFormat.equalsIgnoreCase("%stock_player_" + tradeType.name() + "%")) {
                        if (!product.getLimitValues().isUnlimited(tradeType)) {
                            lore.addAll(VirtualConfig.PRODUCT_FORMAT_LORE_LIMIT.get().getOrDefault(tradeType, Collections.emptyList()));
                        }
                        continue Label_Format;
                    }
                }
                lore.add(lineFormat);
            }

            PlaceholderMap placeholderMap = new PlaceholderMap();
            placeholderMap.getKeys().addAll(product.getPlaceholders(player).getKeys());
            placeholderMap.getKeys().addAll(product.getCurrency().getPlaceholders().getKeys());
            placeholderMap.getKeys().addAll(shop.getPlaceholders().getKeys());
            lore.replaceAll(placeholderMap.replacer());
            if (Config.GUI_PLACEHOLDER_API.get()) {
                lore.replaceAll(str -> PlaceholderAPI.setPlaceholders(player, str));
            }
            meta.setLore(StringUtil.stripEmpty(lore));
        });

        MenuItem menuItem = new MenuItem(preview, Integer.MAX_VALUE, ItemOptions.personalWeak(player), slot);
        menuItem.setClick((viewer, event) -> {
            Player player2 = viewer.getPlayer();
            boolean isBedrock = PlayerUtil.isBedrockPlayer(player2);

            ShopClickAction clickType = Config.GUI_CLICK_ACTIONS.get().get(event.getClick());
            if (isBedrock) {
                boolean isBuyable = shop.isTransactionEnabled(TradeType.BUY) && product.isBuyable();
                boolean isSellable = shop.isTransactionEnabled(TradeType.SELL) && product.isSellable();

                if (isBuyable && !isSellable) clickType = ShopClickAction.BUY_SELECTION;
                else if (isSellable && !isBuyable) clickType = ShopClickAction.SELL_SELECTION;
            }
            if (clickType == null) return;

            if (!product.hasAccess(player)) {
                this.plugin.getMessage(Lang.ERROR_PERMISSION_DENY).send(player);
                return;
            }

            // In case if some "smart" guy have shop GUI opened during the rotation.
            if (product instanceof RotatingProduct rotatingProduct && !rotatingProduct.isInRotation()) {
                this.openNextTick(viewer, viewer.getPage());
                return;
            }

            ShopClickAction finalClickType = clickType;
            this.plugin.runTask(task -> {
                product.prepareTrade(player2, finalClickType);
            });
        });
        this.addItem(menuItem);
    }
}

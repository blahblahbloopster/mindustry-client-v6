package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.input.*;
import arc.math.Mathf;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.core.GameState.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.zip.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class SettingsMenuDialog extends SettingsDialog{
    /** Mods break if these are changed to BetterSettingsTable so instead we cast them into different vars and just use those. */
    public SettingsTable graphics = new BetterSettingsTable(), sound = new BetterSettingsTable(), game = new BetterSettingsTable();
    public BetterSettingsTable realGraphics, realGame, realSound, client;

    private Table prefs;
    private Table menu;
    private BaseDialog dataDialog;
    private boolean wasPaused;

    public SettingsMenuDialog(){
        hidden(() -> {
            Sounds.back.play();
            if(state.isGame()){
                if(!wasPaused || Vars.net.active())
                    state.set(State.playing);
            }
        });

        shown(() -> {
            back();
            if(state.isGame()){
                wasPaused = state.is(State.paused);
                state.set(State.paused);
            }

            rebuildMenu();
        });

        released(() -> {
            if(scene.getKeyboardFocus() != this) return;
            try { // This is the most cancer way to do this but this is what happens when you need to make something public without editing the original file
                Method ohno = SettingsTable.class.getDeclaredMethod("rebuild");
                ohno.setAccessible(true);
                ohno.invoke(ui.settings.client);
            } catch (Exception e) {
                Log.err(e);
            }
        });

        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image()).growX().height(3f).pad(4f).get().setColor(Pal.accent);

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table(Tex.button);

        realGame = (BetterSettingsTable) game;
        realGraphics = (BetterSettingsTable) graphics;
        realSound = (BetterSettingsTable) sound;
        client = new BetterSettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        dataDialog = new BaseDialog("@settings.data");
        dataDialog.addCloseButton();

        dataDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButtonStyle style = Styles.cleart;

            t.button("@settings.cleardata", Icon.trash, style, () -> ui.showConfirm("@confirm", "@settings.clearall.confirm", () -> {
                ObjectMap<String, Object> map = new ObjectMap<>();
                for(String value : Core.settings.keys()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Core.settings.get(value, null));
                    }
                }
                Core.settings.clear();
                Core.settings.putAll(map);

                for(Fi file : dataDirectory.list()){
                    file.deleteDirectory();
                }

                Core.app.exit();
            })).marginLeft(4);

            t.row();

            t.button("@settings.clearsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearsaves.confirm", () -> control.saves.deleteAll());
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearresearch", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearresearch.confirm", () -> {
                    universe.clearLoadoutInfo();
                    for(TechNode node : TechTree.all){
                        node.reset();
                    }
                    content.each(c -> {
                        if(c instanceof UnlockableContent u){
                            u.clearUnlock();
                        }
                    });
                    settings.remove("unlocks");
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearcampaignsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearcampaignsaves.confirm", () -> {
                    for(var planet : content.planets()){
                        for(var sec : planet.sectors){
                            sec.clearInfo();
                            if(sec.save != null){
                                sec.save.delete();
                                sec.save = null;
                            }
                        }
                    }

                    for(var slot : control.saves.getSaveSlots().copy()){
                        if(slot.isSector()){
                            slot.delete();
                        }
                    }
                });
            }).marginLeft(4);

            t.row();

            t.button("@data.export", Icon.upload, style, () -> {
                if(ios){
                    Fi file = Core.files.local("mindustry-data-export.zip");
                    try{
                        exportData(file);
                    }catch(Exception e){
                        ui.showException(e);
                    }
                    platform.shareFile(file);
                }else{
                    platform.showFileChooser(false, "zip", file -> {
                        try{
                            exportData(file);
                            ui.showInfo("@data.exported");
                        }catch(Exception e){
                            e.printStackTrace();
                            ui.showException(e);
                        }
                    });
                }
            }).marginLeft(4);

            t.row();

            t.button("@data.import", Icon.download, style, () -> ui.showConfirm("@confirm", "@data.import.confirm", () -> platform.showFileChooser(true, "zip", file -> {
                try{
                    importData(file);
                    Core.app.exit();
                }catch(IllegalArgumentException e){
                    ui.showErrorMessage("@data.invalid");
                }catch(Exception e){
                    e.printStackTrace();
                    if(e.getMessage() == null || !e.getMessage().contains("too short")){
                        ui.showException(e);
                    }else{
                        ui.showErrorMessage("@data.invalid");
                    }
                }
            }))).marginLeft(4);

            if(!mobile){
                t.row();
                t.button("@data.openfolder", Icon.folder, style, () -> Core.app.openFolder(Core.settings.getDataDirectory().absolutePath())).marginLeft(4);
            }

            t.row();

            t.button("@crash.export", Icon.upload, style, () -> {
                if(settings.getDataDirectory().child("crashes").list().length == 0 && !settings.getDataDirectory().child("last_log.txt").exists()){
                    ui.showInfo("@crash.none");
                }else{
                    if(ios){
                        Fi logs = tmpDirectory.child("logs.txt");
                        logs.writeString(getLogs());
                        platform.shareFile(logs);
                    }else{
                        platform.showFileChooser(false, "txt", file -> {
                            file.writeString(getLogs());
                            app.post(() -> ui.showInfo("@crash.exported"));
                        });
                    }
                }
            }).marginLeft(4);
        });

        ScrollPane pane = new ScrollPane(prefs);
        pane.addCaptureListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                Element actor = pane.hit(x, y, true);
                if(actor instanceof Slider){
                    pane.setFlickScroll(false);
                    return true;
                }

                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                pane.setFlickScroll(true);
                super.touchUp(event, x, y, pointer, button);
            }
        });
        pane.setFadeScrollBars(true);
        pane.setCancelTouchFocus(false);

        row();
        add(pane).grow().top();
        row();
        add(buttons).fillX();

        addSettings();
    }

    String getLogs(){
        Fi log = settings.getDataDirectory().child("last_log.txt");

        StringBuilder out = new StringBuilder();
        for(Fi fi : settings.getDataDirectory().child("crashes").list()){
            out.append(fi.name()).append("\n\n").append(fi.readString()).append("\n");
        }

        if(log.exists()){
            out.append("\nlast log:\n").append(log.readString());
        }

        return out.toString();
    }

    void rebuildMenu(){
        menu.clearChildren();

        TextButtonStyle style = Styles.cleart;

        menu.defaults().size(300f, 60f);
        menu.button("@settings.game", style, () -> visible(0));
        menu.row();
        menu.button("@settings.graphics", style, () -> visible(1));
        menu.row();
        menu.button("@settings.sound", style, () -> visible(2));
        menu.row();
        menu.button("@settings.client", style, () -> visible(3));
        menu.row();
        menu.button("@settings.language", style, ui.language::show);
        if(!mobile || Core.settings.getBool("keyboard")){
            menu.row();
            menu.button("@settings.controls", style, ui.controls::show);
        }

        menu.row();
        menu.button("@settings.data", style, () -> dataDialog.show());
    }

    void addSettings(){
        sound.sliderPref("musicvol", bundle.get("setting.musicvol.name", "Music Volume"), 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("sfxvol", bundle.get("setting.sfxvol.name", "SFX Volume"), 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", bundle.get("setting.ambientvol.name", "Ambient Volume"), 100, 0, 100, 1, i -> i + "%");


        // Client Settings, organized exactly the same as Bundle.properties: text first, sliders second, checked boxes third, unchecked boxes last
        client.category("antigrief");
        client.sliderPref("reactorwarningdistance", 40, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("reactorsounddistance", 25, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorwarningdistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorsounddistance", 3, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagwarningdistance", 10, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagsounddistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.checkPref("breakwarnings", true); // Warnings for removal of certain sandbox stuff (mostly sources)
        client.checkPref("powersplitwarnings", true); // TODO: Add a minimum building requirement and a setting for it
        client.checkPref("viruswarnings", true);
        client.checkPref("removecorenukes", false);

        client.category("chat");
        client.checkPref("clearchatonleave", true);
        client.checkPref("logmsgstoconsole", true);
        client.checkPref("highlightcryptomsg", true);
        client.checkPref("highlightclientmsg", false);
        client.checkPref("displayasuser", false);
        client.checkPref("broadcastcoreattack", false); // TODO: Multiple people using this setting at once will cause chat spam
        client.checkPref("showuserid", false);

        client.category("controls");
        client.checkPref("blockreplace", true);
        client.checkPref("autoboost", false);
        client.checkPref("assumeunstrict", false);

        client.category("graphics");
        client.sliderPref("minzoom", 0, 0, 100, s -> Strings.fixed(Mathf.pow(10, 0.0217f * s) / 100f, 2) + "x");
        client.sliderPref("weatheropacity", 50, 0, 100, s -> s + "%");
        client.sliderPref("effectscl", 100, 0, 100, 5, s -> s + "%");
        client.sliderPref("firescl", 50, 0, 150, 5, s -> s + "%[lightgray] (after stack: " + s * settings.getInt("effectscl") / 100 + "%)[]");
        client.checkPref("tilehud", true);
        client.checkPref("lighting", true);
        client.checkPref("unitranges", false);
        client.checkPref("disablemonofont", false); // Requires Restart
        client.checkPref("drawhitboxes", false);

        client.category("misc");
        client.updatePref();
        client.checkPref("autoupdate", true);
        client.checkPref("discordrpc", true, i -> platform.toggleDiscord(i));
        client.checkPref("allowjoinany", false);
        client.checkPref("debug", false, i -> Log.level = i ? Log.LogLevel.debug : Log.LogLevel.info); // Sets the log level to debug
        // End Client Settings


        game.screenshakePref();
        if(mobile){
            game.checkPref("autotarget", true);
            game.checkPref("keyboard", false, val -> control.setInput(val ? new DesktopInput() : new MobileInput()));
            if(Core.settings.getBool("keyboard")){
                control.setInput(new DesktopInput());
            }
        }
        //the issue with touchscreen support on desktop is that:
        //1) I can't test it
        //2) the SDL backend doesn't support multitouch
        /*else{
            game.checkPref("touchscreen", false, val -> control.setInput(!val ? new DesktopInput() : new MobileInput()));
            if(Core.settings.getBool("touchscreen")){
                control.setInput(new MobileInput());
            }
        }*/
        game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Core.bundle.format("setting.seconds", i));

        if(!mobile){
            game.checkPref("crashreport", true);
        }
        game.checkPref("savecreate", true); // Autosave
        game.checkPref("conveyorpathfinding", true);
        game.checkPref("hints", true);
        game.checkPref("logichints", true);

        if(!mobile){
            game.checkPref("backgroundpause", true);
            game.checkPref("buildautopause", false);
        }

        game.checkPref("doubletapmine", false);

        if(!ios){
            game.checkPref("modcrashdisable", true);
        }

        if(steam){
            game.sliderPref("playerlimit", 16, 2, 250, i -> {
                platform.updateLobby();
                return i + "";
            });

            if(!Version.modifier.contains("beta")){
                game.checkPref("publichost", false, i -> platform.updateLobby());
            }
        }

        graphics.sliderPref("uiscale", 100, 25, 300, 25, s -> {
            if(ui.settings != null){
                Core.settings.put("uiscalechanged", true);
            }
            return s + "%";
        });
        graphics.sliderPref("fpscap", 240, 15, 245, 5, s -> (s > 240 ? Core.bundle.get("setting.fpscap.none") : Core.bundle.format("setting.fpscap.text", s)));
        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");
        graphics.sliderPref("lasersopacity", 100, 0, 100, 5, s -> {
            if(ui.settings != null){
                Core.settings.put("preferredlaseropacity", s);
            }
            return s + "%";
        });
        graphics.sliderPref("bridgeopacity", 100, 0, 100, 5, s -> s + "%");

        if(!mobile){
            graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> {
                if(b){
                    Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode());
                }else{
                    Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                }
            });

            graphics.checkPref("borderlesswindow", false, b -> Core.graphics.setUndecorated(b));

            Core.graphics.setVSync(Core.settings.getBool("vsync"));
            if(Core.settings.getBool("fullscreen")){
                Core.app.post(() -> Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode()));
            }

            if(Core.settings.getBool("borderlesswindow")){
                Core.app.post(() -> Core.graphics.setUndecorated(true));
            }
        }else if(!ios){
            graphics.checkPref("landscape", false, b -> {
                if(b){
                    platform.beginForceLandscape();
                }else{
                    platform.endForceLandscape();
                }
            });

            if(Core.settings.getBool("landscape")){
                platform.beginForceLandscape();
            }
        }

        graphics.checkPref("effects", true);
        graphics.checkPref("atmosphere", !mobile);
        graphics.checkPref("destroyedblocks", true);
        graphics.checkPref("blockstatus", false);
        graphics.checkPref("playerchat", true);
        if(!mobile){
            graphics.checkPref("coreitems", true);
        }
        graphics.checkPref("minimap", !mobile);
        graphics.checkPref("smoothcamera", true);
        graphics.checkPref("position", false);
        graphics.checkPref("fps", false);
        graphics.checkPref("playerindicators", true);
        graphics.checkPref("indicators", true);
        graphics.checkPref("animatedwater", true);
        if(Shaders.shield != null){
            graphics.checkPref("animatedshields", !mobile);
        }

        //if(!ios){
        graphics.checkPref("bloom", true, val -> renderer.toggleBloom(val));
        //}else{
        //    Core.settings.put("bloom", false);
        //}

        graphics.checkPref("pixelate", false, val -> {
            if(val){
                Events.fire(Trigger.enablePixelation);
            }
        });

        graphics.checkPref("linear", !mobile, b -> {
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = b ? TextureFilter.linear : TextureFilter.nearest;
                tex.setFilter(filter, filter);
            }
        });

        if(Core.settings.getBool("linear")){
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = TextureFilter.linear;
                tex.setFilter(filter, filter);
            }
        }

        if(!mobile){
            Core.settings.put("swapdiagonal", false);
        }

        graphics.checkPref("flow", true);
    }


    /** Extends arc's {@link SettingsTable}, allows the addition of custom setting types without editing arc. */
    public static class BetterSettingsTable extends SettingsTable{
        /** Add a section/subcategory. */
        public void category(String name){
            pref(new Category(name));
        }

        /* TODO: Actually add this at some point, this sounds like a massive pain in the ass tho.
        public void textPref(String name, String def){
            settings.defaults(name, def);
            pref(new TextPref(name));
        } */

        // Elements are actually added below
        public static class Category extends Setting{
            Category(String name){
                this.name = name;
                this.title = bundle.get("setting." + name + ".category");
            }

            @Override
            public void add(SettingsTable table){
                table.add("").row(); // Add a cell first as .row doesn't work if there are no cells in the current row.
                table.add("[accent]" + title);
                table.row();
            }
        }


        /** Since the update pref takes half a page and implementing all this in a non static manner is a pain, I'm leaving it here for now. */
        private void updatePref(){
            settings.defaults("updateurl", "blahblahbloopster/mindustry-client-v6");
            if (!Version.updateUrl.isEmpty()) settings.put("updateurl", Version.updateUrl); // overwrites updateurl on every boot, shouldn't be a real issue
            pref(new Setting() {
                boolean urlChanged;

                @Override
                public void add(SettingsTable table) { // Update URL with update button TODO: Move this to TextPref when i decide im willing to spend 6 hours doing so
                    name = "updateurl";
                    title = bundle.get("setting." + name + ".name");

                    table.table(t -> {
                        t.button(Icon.refresh, Styles.settingtogglei, 32, () -> {
                            ui.loadfrag.show();
                            becontrol.checkUpdate(result -> {
                                ui.loadfrag.hide();
                                urlChanged = false;
                                if(!result){
                                    ui.showInfo("@be.noupdates");
                                } else {
                                    becontrol.showUpdateDialog();
                                }
                            });
                        }).update(u -> u.setChecked(becontrol.isUpdateAvailable() || urlChanged)).padRight(4);
                        Label label = new Label(title);
                        t.add(label).minWidth(label.getPrefWidth() / Scl.scl(1.0F) + 50.0F);
                        t.field(settings.getString(name), text -> {
                            becontrol.setUpdateAvailable(false); // Set this to false as we don't know if this is even a valid URL.
                            urlChanged = true;
                            settings.put(name, text);
                        }).growX();
                    }).left().fillX().padTop(3).height(32);
                    table.row();
                }
            });
        }
    }

    public void exportData(Fi file) throws IOException{
        Seq<Fi> files = new Seq<>();
        files.add(Core.settings.getSettingsFile());
        files.addAll(customMapDirectory.list());
        files.addAll(saveDirectory.list());
        files.addAll(screenshotDirectory.list());
        files.addAll(modDirectory.list());
        files.addAll(schematicDirectory.list());
        String base = Core.settings.getDataDirectory().path();

        //add directories
        for(Fi other : files.copy()){
            Fi parent = other.parent();
            while(!files.contains(parent) && !parent.equals(settings.getDataDirectory())){
                files.add(parent);
            }
        }

        try(OutputStream fos = file.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
            for(Fi add : files){
                String path = add.path().substring(base.length());
                if(add.isDirectory()) path += "/";
                //fix trailing / in path
                path = path.startsWith("/") ? path.substring(1) : path;
                zos.putNextEntry(new ZipEntry(path));
                if(!add.isDirectory()){
                    Streams.copy(add.read(), zos);
                }
                zos.closeEntry();
            }
        }
    }

    public void importData(Fi file){
        Fi dest = Core.files.local("zipdata.zip");
        file.copyTo(dest);
        Fi zipped = new ZipFi(dest);

        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid save data.");
        }

        //delete old saves so they don't interfere
        saveDirectory.deleteDirectory();

        //purge existing tmp data, keep everything else
        tmpDirectory.deleteDirectory();

        zipped.walk(f -> f.copyTo(base.child(f.path())));
        dest.delete();

        //clear old data
        settings.clear();
        //load data so it's saved on exit
        settings.load();
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();
        prefs.add(new Table[]{game, graphics, sound, client}[index]);
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, () -> {
            if(prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        }).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                if(prefs.getChildren().first() != menu){
                    back();
                }else{
                    hide();
                }
            }
        });
    }
}

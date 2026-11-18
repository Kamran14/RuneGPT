package com.kamrant.runegpt.service;

import com.kamrant.runegpt.RuneGPTConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Hands the tracked guide's destination to the Shortest Path plugin (Plugin Hub), which draws a full route with
 * teleports, transports and the player's own settings. Plugins can't share classes, so this only uses RuneLite's
 * {@link PluginMessage} event with JDK/RuneLite types, as Shortest Path documents in
 * docs/Cross-plugin-communication.md: {@code path} {target: WorldPoint}, {@code clear}, and {@code getTarget},
 * answered by {@code currentTarget}.
 *
 * <p>Shortest Path is optional. RuneGPT notices it's installed when it answers a {@code getTarget} probe; until then
 * (or when the setting is off) RuneGPT's own minimap arrow and world map line guide the player as before. RuneGPT
 * only clears a path it set itself and that is still showing, never one the player picked.
 */
@Slf4j
@Singleton
public class ShortestPathBridge {

   static final String NAMESPACE = "shortestpath";
   static final String PATH = "path";
   static final String CLEAR = "clear";
   static final String GET_TARGET = "getTarget";
   static final String CURRENT_TARGET = "currentTarget";
   static final String KEY_TARGET = "target";
   static final String KEY_ID = "id";
   static final String KEY_SET = "set";
   static final String PROBE_ID = "runegpt-probe";
   static final String RELEASE_ID = "runegpt-release";

   private final EventBus eventBus;
   private final ClientThread clientThread;
   private final RuneGPTConfig config;

   private volatile boolean installed;
   // The destination RuneGPT last sent and still owns, or null.
   private volatile WorldPoint sent;
   // A destination RuneGPT is giving up: cleared once Shortest Path confirms it's still the one showing.
   private volatile WorldPoint releasing;

   @Inject
   public ShortestPathBridge(final EventBus eventBus, final ClientThread clientThread, final RuneGPTConfig config) {
      this.eventBus = eventBus;
      this.clientThread = clientThread;
      this.config = config;
   }

   /** Asks Shortest Path to answer, so we learn whether it's installed and enabled. Safe from any thread. */
   public void probe() {
      post(GET_TARGET, idData(PROBE_ID));
   }

   /**
    * A plugin was turned on or off: forget what we knew and ask again. A restarted Shortest Path has lost our route,
    * so it's sent again on the next {@link #show}.
    */
   public void onPluginsChanged() {
      installed = false;
      sent = null;
      probe();
   }

   /** True when Shortest Path is drawing the route to RuneGPT's destination, so our own far-away guide can step aside. */
   public boolean isRouting() {
      return installed && sent != null && config.useShortestPath();
   }

   /** True once Shortest Path has answered a {@code getTarget} since it was last turned on. */
   public boolean isInstalled() {
      return installed;
   }

   /** Shows a route to {@code target} (replacing any path). Does nothing if the setting is off. Safe from any thread. */
   public void show(final WorldPoint target) {
      if (target == null || !config.useShortestPath()) {
         release();
         return;
      }
      releasing = null;
      if (target.equals(sent)) {
         return;
      }
      sent = target;
      final Map<String, Object> data = new HashMap<>();
      data.put(KEY_TARGET, target);
      post(PATH, data);
      if (!installed) {
         probe();
      }
   }

   /** Gives up RuneGPT's route; it's cleared only if it's still the path Shortest Path shows. Safe from any thread. */
   public void release() {
      final WorldPoint ours = sent;
      if (ours == null) {
         return;
      }
      sent = null;
      releasing = ours;
      post(GET_TARGET, idData(RELEASE_ID));
   }

   /** Plugin shutdown: there's no time to wait for an answer, so clear our route now if we set one. */
   public void releaseNow() {
      if (sent != null || releasing != null) {
         sent = null;
         releasing = null;
         eventBus.post(new PluginMessage(NAMESPACE, CLEAR, new HashMap<>()));
      }
   }

   /** Forwarded from the plugin's {@code @Subscribe onPluginMessage}. */
   public void onPluginMessage(final PluginMessage event) {
      // Our own path/clear/getTarget messages come back to us too; only replies prove Shortest Path is there.
      if (!NAMESPACE.equals(event.getNamespace()) || !CURRENT_TARGET.equals(event.getName())
         || event.getData() == null) {
         return;
      }
      installed = true;
      final Map<String, Object> data = event.getData();
      if (!RELEASE_ID.equals(data.get(KEY_ID))) {
         return;
      }
      final WorldPoint ours = releasing;
      releasing = null;
      if (ours != null && sent == null && isOnlyTarget(data, ours)) {
         post(CLEAR, new HashMap<>());
      }
   }

   /** True if the shown path leads only to {@code point} (so the player hasn't replaced it with their own). */
   static boolean isOnlyTarget(final Map<String, Object> data, final WorldPoint point) {
      if (!Boolean.TRUE.equals(data.get(KEY_SET))) {
         return false;
      }
      final Object targets = data.get(KEY_TARGET);
      if (targets instanceof Collection<?>) {
         final Collection<?> c = (Collection<?>) targets;
         return c.size() == 1 && point.equals(c.iterator().next());
      }
      return point.equals(targets);
   }

   private static Map<String, Object> idData(final String id) {
      final Map<String, Object> data = new HashMap<>();
      data.put(KEY_ID, id);
      return data;
   }

   // Shortest Path reads the player's location while handling messages, so post on the client thread.
   private void post(final String name, final Map<String, Object> data) {
      clientThread.invokeLater(() -> {
         try {
            eventBus.post(new PluginMessage(NAMESPACE, name, data));
         } catch (final RuntimeException e) {
            log.debug("RuneGPT: Shortest Path message {} failed", name, e);
         }
      });
   }
}

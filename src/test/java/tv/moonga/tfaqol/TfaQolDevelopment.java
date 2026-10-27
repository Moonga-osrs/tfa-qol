package tv.moonga.tfaqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Standalone development entry point. Never packaged as a RuneLite client. */
public final class TfaQolDevelopment {
  private TfaQolDevelopment() {}

  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(TfaQolPlugin.class);
    RuneLite.main(args);
  }
}

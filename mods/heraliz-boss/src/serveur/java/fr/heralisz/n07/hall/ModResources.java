package fr.heralisz.n07.hall;

import fr.heralisz.n07.HeraliszN07;
import net.minecraftforge.fml.ModList;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lecture d'un fichier du jar du mod (structures, reperes) sans dependre d'un rechargement de donnees. */
public final class ModResources {
   private ModResources() {}

   /** path sans « / » initial, ex. data/heralisz_n07/structures/n07_lift.json ; null si absent. */
   public static InputStream open(String path) {
      try {
         var file = ModList.get().getModFileById(HeraliszN07.MODID);
         if (file != null) {
            Path p = file.getFile().findResource(path);
            if (Files.exists(p)) return Files.newInputStream(p);
         }
      } catch (Exception ignored) {
         // repli ci-dessous
      }
      InputStream in = ModResources.class.getResourceAsStream("/" + path);
      return in != null ? in : ModResources.class.getClassLoader().getResourceAsStream(path);
   }
}

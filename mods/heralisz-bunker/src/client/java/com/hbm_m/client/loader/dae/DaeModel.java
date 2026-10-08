package com.hbm_m.client.loader.dae;

import com.hbm_m.lib.RefStrings;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DaeModel {
   public final ResourceLocation resource;
   public final List<DaeNode> sceneRoots = new ArrayList<>();
   public final Map<String, DaeMesh> meshes = new HashMap<>();
   public final Map<String, ResourceLocation> textures = new HashMap<>();
   public final Map<String, DaeAnimation> animations = new HashMap<>();
   public float scale = 1.0F;
   public static final Collection<DaeModel> allModels = Collections.synchronizedCollection(new ArrayList<>());

   public DaeModel(String path) {
      this(RefStrings.resourceLocation(path));
   }

   public DaeModel(ResourceLocation resource) {
      this.resource = resource;
   }

   public static DaeModel load(ResourceLocation resource) {
      DaeModel model = new DaeModel(resource);
      model.parseResource();
      allModels.add(model);
      return model;
   }

   public void reload() {
      this.sceneRoots.clear();
      this.meshes.clear();
      this.textures.clear();
      this.animations.clear();
      this.parseResource();
   }

   private void parseResource() {
      try (InputStream stream = MinecraftResourceResolver.open(this.resource)) {
         this.parse(stream);
      } catch (IOException e) {
         throw new DaeModelFormatException("Failed to read DAE model " + this.resource, e);
      }
   }

   public void clear() {
      this.sceneRoots.clear();
      this.meshes.clear();
      this.textures.clear();
      this.animations.clear();
      allModels.remove(this);
   }

   public static float[] computeNormals(DaeMesh mesh) {
      float[] positions = mesh.positions;
      if (positions == null) {
         return new float[0];
      }

      int count = positions.length / 3;
      float[] normals = new float[positions.length];

      for (int[] tri : mesh.tris) {
         float ax = positions[tri[0] * 3];
         float ay = positions[tri[0] * 3 + 1];
         float az = positions[tri[0] * 3 + 2];
         float bx = positions[tri[3] * 3];
         float by = positions[tri[3] * 3 + 1];
         float bz = positions[tri[3] * 3 + 2];
         float cx = positions[tri[6] * 3];
         float cy = positions[tri[6] * 3 + 1];
         float cz = positions[tri[6] * 3 + 2];
         float ux = bx - ax;
         float uy = by - ay;
         float uz = bz - az;
         float vx = cx - ax;
         float vy = cy - ay;
         float vz = cz - az;
         float nx = uy * vz - uz * vy;
         float ny = uz * vx - ux * vz;
         float nz = ux * vy - uy * vx;

         for (int i = 0; i < 3; i++) {
            int idx = tri[i * 3] * 3;
            normals[idx] += nx;
            normals[idx + 1] = normals[idx + 1] + ny;
            normals[idx + 2] = normals[idx + 2] + nz;
         }
      }

      for (int i = 0; i < count; i++) {
         float x = normals[i * 3];
         float y = normals[i * 3 + 1];
         float z = normals[i * 3 + 2];
         float len = (float)Math.sqrt(x * x + y * y + z * z);
         if (len > 1.0E-6F) {
            normals[i * 3] = x / len;
            normals[i * 3 + 1] = y / len;
            normals[i * 3 + 2] = z / len;
         } else {
            normals[i * 3 + 1] = 1.0F;
         }
      }

      return normals;
   }

   private void parse(InputStream stream) {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setNamespaceAware(true);
         factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
         Document doc = factory.newDocumentBuilder().parse(stream);
         Element root = doc.getDocumentElement();
         if (root != null && "COLLADA".equals(root.getNodeName())) {
            this.parseGeometries(root);
            Map<String, String> materialEffects = this.parseMaterials(root);
            Map<String, String> effectImages = this.parseEffects(root);
            Map<String, String> imagePaths = this.parseImages(root);
            this.buildTextureMap(root, materialEffects, effectImages, imagePaths);
            this.parseScene(root);
            this.parseAnimations(root);
         } else {
            throw new DaeModelFormatException("Not a COLLADA document: " + this.resource);
         }
      } catch (ParserConfigurationException | SAXException | IOException e) {
         throw new DaeModelFormatException("Malformed XML in " + this.resource, e);
      }
   }

   private void parseGeometries(Element root) {
      for (Element library : children(root, "library_geometries")) {
         for (Element geo : children(library, "geometry")) {
            String id = geo.getAttribute("id");
            if (!id.isEmpty()) {
               Element meshEl = firstChild(geo, "mesh");
               if (meshEl != null) {
                  DaeMesh mesh = new DaeMesh();

                  for (Element primitive : union(meshEl, "polylist", "triangles")) {
                     this.parsePrimitive(meshEl, primitive, mesh);
                  }

                  this.meshes.put(id, mesh);
               }
            }
         }
      }
   }

   private void parsePrimitive(Element meshEl, Element prim, DaeMesh mesh) {
      Map<String, Integer> semanticOffset = new HashMap<>();
      int maxOffset = 0;

      for (Element input : children(prim, "input")) {
         String semantic = input.getAttribute("semantic");
         int offset = parseInt(input.getAttribute("offset"), 0);
         semanticOffset.put(semantic, offset);
         maxOffset = Math.max(maxOffset, offset + 1);
      }

      int posOffset;
      if (semanticOffset.containsKey("VERTEX")) {
         posOffset = semanticOffset.get("VERTEX");
      } else {
         posOffset = semanticOffset.getOrDefault("POSITION", 0);
      }

      int normalOffset = semanticOffset.getOrDefault("NORMAL", 1);
      int uvOffset = semanticOffset.getOrDefault("TEXCOORD", 2);
      boolean haveNormals = semanticOffset.containsKey("NORMAL");
      boolean haveUvs = semanticOffset.containsKey("TEXCOORD");
      float[] positions = this.readSource(meshEl, posOffset, prim);
      if (positions != null) {
         if (mesh.positions == null) {
            mesh.positions = positions;
            mesh.normals = haveNormals ? this.readSource(meshEl, semanticOffset.get("NORMAL"), prim) : null;
            mesh.uvs = haveUvs ? this.readSource(meshEl, semanticOffset.get("TEXCOORD"), prim) : null;
         }

         int[] indices = parseIntArray(textOf(prim, "p"));
         if (indices != null && indices.length != 0) {
            if (prim.getNodeName().equals("polylist")) {
               int[] vcount = parseIntArray(textOf(prim, "vcount"));
               if (vcount == null) {
                  return;
               }

               int ptr = 0;

               for (int count : vcount) {
                  int[] poly = new int[count * 3];

                  for (int i = 0; i < count; i++) {
                     poly[i * 3] = indices[ptr + i * maxOffset + posOffset];
                     poly[i * 3 + 1] = haveNormals ? indices[ptr + i * maxOffset + normalOffset] : -1;
                     poly[i * 3 + 2] = haveUvs ? indices[ptr + i * maxOffset + uvOffset] : -1;
                  }

                  ptr += count * maxOffset;

                  for (int i = 1; i < count - 1; i++) {
                     addTriangle(mesh.tris, poly, 0, i * 3, (i + 1) * 3);
                  }
               }
            } else {
               int count = parseInt(prim.getAttribute("count"), indices.length / maxOffset / 3);

               for (int i = 0; i < count; i++) {
                  int base = i * maxOffset * 3;
                  int[] tri = new int[9];

                  for (int j = 0; j < 3; j++) {
                     tri[j * 3] = indices[base + j * maxOffset + posOffset];
                     tri[j * 3 + 1] = haveNormals ? indices[base + j * maxOffset + normalOffset] : -1;
                     tri[j * 3 + 2] = haveUvs ? indices[base + j * maxOffset + uvOffset] : -1;
                  }

                  mesh.tris.add(tri);
               }
            }
         }
      }
   }

   private static void addTriangle(List<int[]> tris, int[] corners, int a, int b, int c) {
      tris.add(new int[]{corners[a], corners[a + 1], corners[a + 2], corners[b], corners[b + 1], corners[b + 2], corners[c], corners[c + 1], corners[c + 2]});
   }

   private float[] readSource(Element meshEl, Integer offset, Element prim) {
      if (offset == null) {
         return null;
      }

      for (Element input : children(prim, "input")) {
         if (parseInt(input.getAttribute("offset"), 0) == offset) {
            return readFloatArray(meshEl, input.getAttribute("source"));
         }
      }

      return null;
   }

   private Map<String, String> parseMaterials(Element root) {
      Map<String, String> map = new HashMap<>();

      for (Element library : children(root, "library_materials")) {
         for (Element mat : children(library, "material")) {
            String id = mat.getAttribute("id");

            for (Element effect : children(mat, "instance_effect")) {
               map.put(id, effect.getAttribute("url").replace("#", ""));
            }
         }
      }

      return map;
   }

   private Map<String, String> parseEffects(Element root) {
      Map<String, String> map = new HashMap<>();

      for (Element library : children(root, "library_effects")) {
         for (Element fx : children(library, "effect")) {
            String id = fx.getAttribute("id");

            for (Element technique : children(fx, "technique")) {
               for (Element shade : children(technique, "phong", "lambert", "blinn", "constant")) {
                  for (Element diffuse : children(shade, "diffuse")) {
                     Element tex = firstChild(diffuse, "texture");
                     if (tex != null) {
                        String sampler = tex.getAttribute("texture");
                        map.put(id, sampler);
                        break;
                     }
                  }
               }
            }
         }
      }

      return map;
   }

   private Map<String, String> parseImages(Element root) {
      Map<String, String> map = new HashMap<>();

      for (Element library : children(root, "library_images")) {
         for (Element image : children(library, "image")) {
            String id = image.getAttribute("id");
            String path = textOf(image, "init_from");
            map.put(id, path);
         }
      }

      return map;
   }

   private void buildTextureMap(Element root, Map<String, String> materialEffects, Map<String, String> effectImages, Map<String, String> imagePaths) {
      Map<String, String> samplerImages = new HashMap<>();
      Map<String, String> surfaceImages = new HashMap<>();

      for (Element library : libraries(root, "library_effects")) {
         for (Element fx : children(library, "effect")) {
            for (Element technique : children(fx, "technique")) {
               for (Element samplerEl : children(technique, "newparam")) {
                  Element surface = firstChild(samplerEl, "surface");
                  if (surface != null) {
                     surfaceImages.put(samplerEl.getAttribute("sid"), textOf(surface, "init_from"));
                  }

                  Element sampler2d = firstChild(samplerEl, "sampler2D");
                  if (sampler2d != null) {
                     String source = textOf(sampler2d, "source");
                     String samplerName = samplerEl.getAttribute("sid");
                     samplerImages.put(samplerName, source);
                  }
               }
            }
         }
      }

      Map<String, String> effectTextures = new HashMap<>();

      for (String effectId : effectImages.keySet()) {
         String sampler = effectImages.get(effectId);
         String image = samplerImages.get(sampler);
         if (image == null) {
            image = sampler;
         }

         String imageId = surfaceImages.get(image);
         if (imageId != null) {
            image = imageId;
         }

         effectTextures.put(effectId, image);
      }

      for (Element library : libraries(root, "library_geometries")) {
         for (Element geo : children(library, "geometry")) {
            Element mesh = firstChild(geo, "mesh");
            if (mesh != null) {
               for (Element prim : union(mesh, "polylist", "triangles")) {
                  String materialName = prim.getAttribute("material");
                  if (!materialName.isEmpty()) {
                     String effectId = materialEffects.get(materialName);
                     if (effectId != null) {
                        String image = effectTextures.get(effectId);
                        if (image != null) {
                           String path = imagePaths.get(image);
                           if (path == null) {
                              path = image;
                           }

                           this.textures.put(materialName, this.resolveTexture(path));
                        }
                     }
                  }
               }
            }
         }
      }

      if (this.textures.isEmpty()) {
         Iterator var22 = imagePaths.values().iterator();
         if (var22.hasNext()) {
            String path = (String)var22.next();
            this.textures.put("default", this.resolveTexture(path));
         }
      }
   }

   private ResourceLocation resolveTexture(String path) {
      if (path != null && !path.isBlank()) {
         if (path.startsWith("file://")) {
            path = path.substring("file://".length());
         }

         String clean = path.replace('\\', '/').trim();

         while (clean.startsWith("/")) {
            clean = clean.substring(1);
         }

         if (clean.startsWith("textures/")) {
            clean = clean.substring("textures/".length());
         } else if (clean.startsWith("assets/")) {
            clean = clean.substring("assets/".length());
            int slash = clean.indexOf(47);
            if (slash >= 0) {
               clean = clean.substring(slash + 1);
            }
         }

         if (!clean.startsWith("textures/") && !clean.startsWith("block/") && !clean.startsWith("item/") && !clean.startsWith("entity/")) {
            int slash = clean.lastIndexOf(47);
            if (slash >= 0) {
               clean = clean.substring(slash + 1);
            }

            clean = "textures/models/" + clean;
         }

         int dot = clean.lastIndexOf(46);
         if (dot >= 0) {
            clean = clean.substring(0, dot);
         }

         return RefStrings.resourceLocation(clean);
      } else {
         return RefStrings.resourceLocation("textures/models/missing");
      }
   }

   private void parseScene(Element root) {
      for (Element library : children(root, "library_visual_scenes")) {
         for (Element scene : children(library, "visual_scene")) {
            for (Element nodeEl : children(scene, "node")) {
               DaeNode node = this.parseNode(nodeEl);
               if (node != null) {
                  this.sceneRoots.add(node);
               }
            }
         }
      }
   }

   private DaeNode parseNode(Element nodeEl) {
      String name = nodeEl.getAttribute("name");
      if (name.isEmpty()) {
         name = nodeEl.getAttribute("id");
      }

      if (name.isEmpty()) {
         name = "node";
      }

      DaeNode node = new DaeNode(name);
      node.colladaId = nodeEl.getAttribute("id");

      for (Element child : children(nodeEl)) {
         switch (child.getNodeName()) {
            case "translate": {
               String sid = child.getAttribute("sid");
               node.transforms.add(new DaeTransform(DaeTransform.Type.TRANSLATE, sid, parseFloatArray(textOf(child))));
               break;
            }
            case "rotate": {
               String sid = child.getAttribute("sid");
               node.transforms.add(new DaeTransform(DaeTransform.Type.ROTATE, sid, parseFloatArray(textOf(child))));
               break;
            }
            case "scale": {
               String sid = child.getAttribute("sid");
               node.transforms.add(new DaeTransform(DaeTransform.Type.SCALE, sid, parseFloatArray(textOf(child))));
               break;
            }
            case "matrix": {
               String sid = child.getAttribute("sid");
               node.transforms.add(new DaeTransform(DaeTransform.Type.MATRIX, sid, parseFloatArray(textOf(child))));
               break;
            }
            case "instance_geometry":
               String url = child.getAttribute("url").replace("#", "");
               DaeMesh mesh = this.meshes.get(url);
               if (mesh == null) {
                  break;
               }

               node.mesh = mesh;
               String material = null;
               Element instance = firstChild(child, "bind_material");
               if (instance != null) {
                  Element technique = firstChild(instance, "technique_common");
                  if (technique != null) {
                     for (Element bind : children(technique, "instance_material")) {
                        material = bind.getAttribute("target").replace("#", "");
                     }
                  }
               }

               if (material != null && this.textures.containsKey(material)) {
                  node.texture = this.textures.get(material);
                  break;
               }

               node.texture = this.firstTexture();
               break;
            case "node":
               DaeNode childNode = this.parseNode(child);
               if (childNode != null) {
                  node.children.add(childNode);
               }
         }
      }

      return node;
   }

   private ResourceLocation firstTexture() {
      Iterator var1 = this.textures.values().iterator();
      return var1.hasNext() ? (ResourceLocation)var1.next() : null;
   }

   private void parseAnimations(Element root) {
      for (Element library : children(root, "library_animations")) {
         String clipName = library.getAttribute("id");
         if (clipName.isEmpty()) {
            clipName = "animation";
         }

         DaeAnimation clip = new DaeAnimation(clipName);
         Map<String, Element> samplerById = new HashMap<>();

         for (Element anim : children(library, "animation")) {
            this.collectSamplers(anim, samplerById);
         }

         for (Element anim : children(library, "animation")) {
            this.collectChannels(anim, root, samplerById, clip);
         }

         if (!clip.isEmpty()) {
            this.animations.put(clipName, clip);
         }
      }
   }

   private void collectSamplers(Element anim, Map<String, Element> samplerById) {
      for (Element sampler : children(anim, "sampler")) {
         samplerById.put(sampler.getAttribute("id"), sampler);
      }

      for (Element child : children(anim, "animation")) {
         this.collectSamplers(child, samplerById);
      }
   }

   private void collectChannels(Element anim, Element root, Map<String, Element> samplerById, DaeAnimation clip) {
      for (Element channel : children(anim, "channel")) {
         this.buildChannel(root, channel, samplerById, clip);
      }

      for (Element child : children(anim, "animation")) {
         this.collectChannels(child, root, samplerById, clip);
      }
   }

   private void buildChannel(Element root, Element channel, Map<String, Element> samplerById, DaeAnimation clip) {
      String target = channel.getAttribute("target");
      String samplerId = channel.getAttribute("source").replace("#", "");
      Element sampler = samplerById.get(samplerId);
      if (sampler != null && target != null && !target.isEmpty()) {
         int slash = target.indexOf(47);
         if (slash >= 0) {
            String nodeName = target.substring(0, slash);
            String property = target.substring(slash + 1);
            Element samplerInput = null;
            Element samplerOutput = null;
            Element samplerInterp = null;

            for (Element input : children(sampler, "input")) {
               switch (input.getAttribute("semantic")) {
                  case "INPUT":
                     samplerInput = input;
                     break;
                  case "OUTPUT":
                     samplerOutput = input;
                     break;
                  case "INTERPOLATION":
                     samplerInterp = input;
               }
            }

            if (samplerInput != null && samplerOutput != null) {
               float[] times = this.readAnimationSource(root, samplerInput.getAttribute("source"));
               float[] values = this.readAnimationSource(root, samplerOutput.getAttribute("source"));
               String[] interps = samplerInterp != null ? this.readStringSource(root, samplerInterp.getAttribute("source")) : null;
               if (times != null && times.length != 0 && values != null) {
                  String suffix = property.substring(property.lastIndexOf(46) + 1);
                  int stride;
                  if (!suffix.equals("ANGLE") && (suffix.length() != 1 || "XYZ".indexOf(suffix) < 0)) {
                     if (property.equals("matrix")) {
                        stride = 16;
                     } else {
                        stride = values.length / times.length;
                        if (stride < 1) {
                           stride = 1;
                        }
                     }
                  } else {
                     stride = 1;
                  }

                  if (values.length >= times.length * stride) {
                     float[][] rows = new float[times.length][stride];

                     for (int i = 0; i < times.length; i++) {
                        System.arraycopy(values, i * stride, rows[i], 0, stride);
                     }

                     boolean[] step = new boolean[times.length];

                     for (int i = 0; i < times.length; i++) {
                        if (interps != null && i < interps.length && !"LINEAR".equals(interps[i])) {
                           step[i] = true;
                        }
                     }

                     if (stride == 1) {
                        float[][] flat = new float[times.length][1];

                        for (int i = 0; i < times.length; i++) {
                           flat[i][0] = rows[i][0];
                        }

                        clip.addChannel(nodeName, property, new DaeCurve(times, flat, step, 1));
                     } else if (stride == 3) {
                        String axis = suffix.length() == 1 && "XYZ".indexOf(suffix) >= 0 ? suffix : null;
                        String baseProp = axis != null ? property.substring(0, property.length() - 2) : property;
                        if (axis != null) {
                           int comp = "XYZ".indexOf(axis);
                           float[][] flat = new float[times.length][1];

                           for (int i = 0; i < times.length; i++) {
                              flat[i][0] = rows[i][comp];
                           }

                           clip.addChannel(nodeName, baseProp + "." + axis, new DaeCurve(times, flat, step, 1));
                        } else {
                           clip.addChannel(nodeName, property, new DaeCurve(times, rows, step, 3));
                        }
                     } else {
                        clip.addChannel(nodeName, property, new DaeCurve(times, rows, step, stride));
                     }
                  }
               }
            }
         }
      }
   }

   private float[] readAnimationSource(Element root, String sourceId) {
      Element source = this.findSource(root, sourceId);
      return source == null ? null : parseFloatArray(textOf(source, "float_array"));
   }

   private String[] readStringSource(Element root, String sourceId) {
      Element source = this.findSource(root, sourceId);
      if (source == null) {
         return null;
      }

      String[] words = textOf(source, "Name_array", "IDREF_array").trim().split("\\s+");
      List<String> result = new ArrayList<>();

      for (String word : words) {
         if (!word.isEmpty()) {
            result.add(word);
         }
      }

      return result.toArray(new String[0]);
   }

   private Element findSource(Element root, String sourceId) {
      if (sourceId.startsWith("#")) {
         sourceId = sourceId.substring(1);
      }

      if (sourceId.isEmpty()) {
         return null;
      }

      for (Element source : descendants(root, "source")) {
         if (sourceId.equals(source.getAttribute("id"))) {
            return source;
         }
      }

      return null;
   }

   private static List<Element> libraries(Element root, String libraryName) {
      return children(root, libraryName);
   }

   private static List<Element> children(Element parent, String... names) {
      List<Element> result = new ArrayList<>();
      NodeList list = parent.getChildNodes();

      for (int i = 0; i < list.getLength(); i++) {
         if (list.item(i) instanceof Element el) {
            for (String name : names) {
               if (name.equals(el.getNodeName())) {
                  result.add(el);
                  break;
               }
            }
         }
      }

      return result;
   }

   private static List<Element> children(Element parent) {
      List<Element> result = new ArrayList<>();
      NodeList list = parent.getChildNodes();

      for (int i = 0; i < list.getLength(); i++) {
         if (list.item(i) instanceof Element el) {
            result.add(el);
         }
      }

      return result;
   }

   private static Element firstChild(Element parent, String name) {
      Iterator var2 = children(parent, name).iterator();
      return var2.hasNext() ? (Element)var2.next() : null;
   }

   private static List<Element> union(Element parent, String... names) {
      return children(parent, names);
   }

   private static List<Element> descendants(Element root, String... tags) {
      List<Element> result = new ArrayList<>();
      collectDescendants(root, tags, result);
      return result;
   }

   private static void collectDescendants(Element parent, String[] tags, List<Element> out) {
      for (Element el : children(parent)) {
         for (String tag : tags) {
            if (tag.equals(el.getNodeName())) {
               out.add(el);
            }
         }

         collectDescendants(el, tags, out);
      }
   }

   private static String textOf(Element el) {
      return el == null ? "" : el.getTextContent().trim();
   }

   private static String textOf(Element parent, String... childNames) {
      for (String name : childNames) {
         Element child = firstChild(parent, name);
         if (child != null) {
            return child.getTextContent().trim();
         }
      }

      return "";
   }

   private static int parseInt(String text, int fallback) {
      try {
         return Integer.parseInt(text.trim());
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private static int[] parseIntArray(String text) {
      String[] parts = text.trim().split("\\s+");
      if (parts.length == 1 && parts[0].isEmpty()) {
         return null;
      }

      int[] result = new int[parts.length];

      for (int i = 0; i < parts.length; i++) {
         try {
            result[i] = Integer.parseInt(parts[i]);
         } catch (NumberFormatException e) {
            return null;
         }
      }

      return result;
   }

   private static float[] parseFloatArray(String text) {
      String[] parts = text.trim().split("\\s+");
      if (parts.length == 1 && parts[0].isEmpty()) {
         return new float[0];
      }

      float[] result = new float[parts.length];

      for (int i = 0; i < parts.length; i++) {
         try {
            result[i] = Float.parseFloat(parts[i]);
         } catch (NumberFormatException e) {
            return new float[0];
         }
      }

      return result;
   }

   private static float[] readFloatArray(Element meshEl, String source) {
      if (source.startsWith("#")) {
         source = source.substring(1);
      }

      if (source.isEmpty()) {
         return null;
      }

      for (Element vertices : descendants(meshEl, "vertices")) {
         if (source.equals(vertices.getAttribute("id"))) {
            Element posInput = firstChild(vertices, "input");
            if (posInput != null) {
               return readFloatArray(meshEl, posInput.getAttribute("source"));
            }
         }
      }

      for (Element sourceEl : descendants(meshEl, "source")) {
         if (source.equals(sourceEl.getAttribute("id"))) {
            return parseFloatArray(textOf(sourceEl, "float_array"));
         }
      }

      return null;
   }

   public ResourceLocation getTexture() {
      return this.firstTexture();
   }
}

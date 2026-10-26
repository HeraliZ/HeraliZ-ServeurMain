package com.hbm_m.client.render.shader;

import com.hbm_m.client.render.ModShaders;
import com.hbm_m.main.MainRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class IrisExtendedShaderAccess {
   private static volatile boolean reflectionInitialized = false;
   private static volatile boolean reflectionAvailable = false;
   private static Method getPipelineManager;
   private static Method getPipelineNullable;
   private static Method getShaderMap;
   private static Method shaderMapGetShader;
   private static Class<?> shaderRenderingPipelineClass;
   private static Class<?> shaderKeyClass;
   private static final Map<String, Object> shaderKeyCache = new HashMap<>();
   private static volatile Object[] mainShaderKeysResolved;
   private static volatile Object[] shadowShaderKeysResolved;
   private static volatile boolean shaderKeyCandidatesBuilt = false;
   private static volatile Object lastResolvedMainKey = null;
   private static volatile Object lastResolvedShadowKey = null;
   private static final AtomicLong currentPassId = new AtomicLong(0L);
   private static volatile long cachedMainPassId = -1L;
   private static volatile long cachedShadowPassId = -1L;
   private static volatile ShaderInstance cachedMainShader = null;
   private static volatile ShaderInstance cachedShadowShader = null;
   private static final AtomicLong pipelineGeneration = new AtomicLong(0L);
   private static volatile Object lastSeenPipelineIdentity = null;
   private static Object capturedRenderingStateInstance;
   private static Method setCurrentBlockEntityMethod;
   private static Method getCurrentBlockEntityMethod;
   private static MethodHandle setCurrentBlockEntityMH;
   private static MethodHandle getCurrentBlockEntityMH;
   private static final String[] MAIN_SHADER_KEY_NAMES = new String[]{
      "BLOCK_ENTITY", "BLOCK_ENTITY_DIFFUSE", "ENTITIES_CUTOUT", "BLOCK", "TERRAIN_CUTOUT", "TERRAIN"
   };
   private static final String[] SHADOW_SHADER_KEY_NAMES = new String[]{
      "SHADOW_ENTITIES_CUTOUT", "SHADOW_BLOCK_ENTITY", "SHADOW_TERRAIN_CUTOUT", "SHADOW_TERRAIN"
   };

   private IrisExtendedShaderAccess() {
   }

   public static long getPipelineGeneration() {
      return pipelineGeneration.get();
   }

   public static void tickPass() {
      currentPassId.incrementAndGet();
      if (!reflectionInitialized) {
         initReflection();
      }

      if (reflectionAvailable) {
         try {
            Object pipelineManager = getPipelineManager.invoke(null);
            if (pipelineManager == null) {
               return;
            }

            Object pipeline = getPipelineNullable.invoke(pipelineManager);
            if (pipeline != lastSeenPipelineIdentity) {
               lastSeenPipelineIdentity = pipeline;
               long gen = pipelineGeneration.incrementAndGet();
               invalidateShaderCache();
               IrisRenderBatch.invalidateCaches();
               MainRegistry.LOGGER.debug("IrisExtendedShaderAccess: pipeline identity changed, generation bumped to {}", gen);
            }
         } catch (Throwable var4) {
         }
      }
   }

   public static void invalidateShaderCache() {
      cachedMainShader = null;
      cachedShadowShader = null;
      cachedMainPassId = -1L;
      cachedShadowPassId = -1L;
   }

   public static ShaderInstance getBlockShader(boolean shadowPass) {
      long pass = currentPassId.get();
      if (shadowPass) {
         ShaderInstance cached = cachedShadowShader;
         if (cached != null && cachedShadowPassId == pass) {
            return cached;
         }
      } else {
         ShaderInstance cached = cachedMainShader;
         if (cached != null && cachedMainPassId == pass) {
            return cached;
         }
      }

      ShaderInstance result;
      if (lookupExtendedShader(shadowPass) instanceof ShaderInstance casted) {
         result = casted;
      } else {
         result = ModShaders.getBlockLitSimpleShader();
      }

      if (shadowPass) {
         cachedShadowShader = result;
         cachedShadowPassId = pass;
      } else {
         cachedMainShader = result;
         cachedMainPassId = pass;
      }

      return result;
   }

   public static int setCurrentRenderedBlockEntity(int value) {
      if (!reflectionInitialized) {
         initReflection();
      }

      if (reflectionAvailable && capturedRenderingStateInstance != null) {
         try {
            if (setCurrentBlockEntityMH != null) {
               int previous = getCurrentBlockEntityMH != null ? (int)getCurrentBlockEntityMH.invokeExact((Object)capturedRenderingStateInstance) : 0;
               setCurrentBlockEntityMH.invokeExact((Object)capturedRenderingStateInstance, (int)value);
               return previous;
            }

            if (setCurrentBlockEntityMethod == null) {
               return Integer.MIN_VALUE;
            }

            int previous = getCurrentBlockEntityMethod != null ? (Integer)getCurrentBlockEntityMethod.invoke(capturedRenderingStateInstance) : 0;
            setCurrentBlockEntityMethod.invoke(capturedRenderingStateInstance, value);
            return previous;
         } catch (Throwable t) {
            return Integer.MIN_VALUE;
         }
      } else {
         return Integer.MIN_VALUE;
      }
   }

   public static void restoreCurrentRenderedBlockEntity(int previous) {
      if (previous != Integer.MIN_VALUE) {
         if (reflectionAvailable && capturedRenderingStateInstance != null) {
            try {
               if (setCurrentBlockEntityMH != null) {
                  setCurrentBlockEntityMH.invokeExact((Object)capturedRenderingStateInstance, (int)previous);
                  return;
               }

               if (setCurrentBlockEntityMethod != null) {
                  setCurrentBlockEntityMethod.invoke(capturedRenderingStateInstance, previous);
               }
            } catch (Throwable var2) {
            }
         }
      }
   }

   public static boolean isReflectionAvailable() {
      if (!reflectionInitialized) {
         initReflection();
      }

      return reflectionAvailable;
   }

   private static Object lookupExtendedShader(boolean shadowPass) {
      if (!reflectionInitialized) {
         initReflection();
      }

      if (!reflectionAvailable) {
         return null;
      }

      try {
         Object pipelineManager = getPipelineManager.invoke(null);
         if (pipelineManager == null) {
            return null;
         }

         Object pipeline = getPipelineNullable.invoke(pipelineManager);
         if (pipeline == null) {
            return null;
         }

         if (!shaderRenderingPipelineClass.isInstance(pipeline)) {
            return null;
         }

         Object shaderMap = getShaderMap.invoke(pipeline);
         if (shaderMap == null) {
            return null;
         }

         ensureShaderKeyCandidatesBuilt();
         Object[] candidates = shadowPass ? shadowShaderKeysResolved : mainShaderKeysResolved;

         for (int i = 0; i < candidates.length; i++) {
            Object key = candidates[i];
            Object instance = shaderMapGetShader.invoke(shaderMap, key);
            if (instance != null) {
               if (shadowPass && lastResolvedShadowKey != key) {
                  lastResolvedShadowKey = key;
                  MainRegistry.LOGGER.info("IrisExtendedShaderAccess: shadow shader resolved to {}", key);
               } else if (!shadowPass && lastResolvedMainKey != key) {
                  lastResolvedMainKey = key;
                  MainRegistry.LOGGER.info("IrisExtendedShaderAccess: main shader resolved to {}", key);
               }

               return instance;
            }
         }

         return null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static void ensureShaderKeyCandidatesBuilt() {
      if (!shaderKeyCandidatesBuilt) {
         synchronized (shaderKeyCache) {
            if (!shaderKeyCandidatesBuilt) {
               mainShaderKeysResolved = resolveShaderKeyCandidatesArray(MAIN_SHADER_KEY_NAMES);
               shadowShaderKeysResolved = resolveShaderKeyCandidatesArray(SHADOW_SHADER_KEY_NAMES);
               shaderKeyCandidatesBuilt = true;
            }
         }
      }
   }

   private static Object[] resolveShaderKeyCandidatesArray(String[] names) {
      Object[] tmp = new Object[names.length];
      int n = 0;

      for (String name : names) {
         Object k = resolveSingleKey(name);
         if (k != null) {
            tmp[n++] = k;
         }
      }

      if (n == names.length) {
         return tmp;
      }

      Object[] trimmed = new Object[n];
      System.arraycopy(tmp, 0, trimmed, 0, n);
      return trimmed;
   }

   private static Object resolveSingleKey(String name) {
      Object cached = shaderKeyCache.get(name);
      if (cached != null) {
         return cached;
      }

      try {
         Object value = Enum.valueOf(shaderKeyClass.asSubclass(Enum.class), name);
         shaderKeyCache.put(name, value);
         return value;
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static synchronized void initReflection() {
      if (!reflectionInitialized) {
         reflectionInitialized = true;
         if (net.minecraftforge.fml.ModList.get().isLoaded("oculus") || net.minecraftforge.fml.ModList.get().isLoaded("iris")) {
            String[] irisClassCandidates = new String[]{"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris"};
            String[] pipelineManagerCandidates = new String[]{"net.irisshaders.iris.pipeline.PipelineManager", "net.coderbot.iris.pipeline.PipelineManager"};
            String[] shaderRenderingPipelineCandidates = new String[]{
               "net.irisshaders.iris.pipeline.ShaderRenderingPipeline",
               "net.irisshaders.iris.pipeline.programs.ShaderRenderingPipeline",
               "net.coderbot.iris.pipeline.ShaderRenderingPipeline",
               "net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline"
            };
            String[] shaderMapCandidates = new String[]{"net.irisshaders.iris.pipeline.programs.ShaderMap", "net.coderbot.iris.pipeline.newshader.ShaderMap"};
            String[] shaderKeyCandidates = new String[]{"net.irisshaders.iris.pipeline.programs.ShaderKey", "net.coderbot.iris.pipeline.newshader.ShaderKey"};
            String[] capturedRenderingStateCandidates = new String[]{
               "net.irisshaders.iris.uniforms.CapturedRenderingState", "net.coderbot.iris.uniforms.CapturedRenderingState"
            };

            try {
               Class<?> irisClass = firstClass(irisClassCandidates);
               getPipelineManager = irisClass.getMethod("getPipelineManager");
               Class<?> pipelineManagerClass = firstClass(pipelineManagerCandidates);
               getPipelineNullable = pipelineManagerClass.getMethod("getPipelineNullable");
               shaderRenderingPipelineClass = firstClass(shaderRenderingPipelineCandidates);
               getShaderMap = shaderRenderingPipelineClass.getMethod("getShaderMap");
               Class<?> shaderMapClass = firstClass(shaderMapCandidates);
               shaderKeyClass = firstClass(shaderKeyCandidates);
               shaderMapGetShader = shaderMapClass.getMethod("getShader", shaderKeyClass);

               try {
                  Class<?> capturedClass = firstClass(capturedRenderingStateCandidates);
                  capturedRenderingStateInstance = capturedClass.getField("INSTANCE").get(null);
                  setCurrentBlockEntityMethod = capturedClass.getMethod("setCurrentBlockEntity", int.class);

                  try {
                     getCurrentBlockEntityMethod = capturedClass.getMethod("getCurrentRenderedBlockEntity");
                  } catch (NoSuchMethodException ignored) {
                     getCurrentBlockEntityMethod = null;
                  }

                  try {
                     Lookup lookup = MethodHandles.lookup();
                     setCurrentBlockEntityMethod.setAccessible(true);
                     setCurrentBlockEntityMH = lookup.unreflect(setCurrentBlockEntityMethod).asType(MethodType.methodType(void.class, Object.class, int.class));
                     if (getCurrentBlockEntityMethod != null) {
                        getCurrentBlockEntityMethod.setAccessible(true);
                        getCurrentBlockEntityMH = lookup.unreflect(getCurrentBlockEntityMethod).asType(MethodType.methodType(int.class, Object.class));
                     } else {
                        getCurrentBlockEntityMH = null;
                     }
                  } catch (Throwable mhFail) {
                     MainRegistry.LOGGER.warn("IrisExtendedShaderAccess: MethodHandle binding failed ({}), falling back to Method.invoke", mhFail.toString());
                     setCurrentBlockEntityMH = null;
                     getCurrentBlockEntityMH = null;
                  }
               } catch (Throwable t) {
                  MainRegistry.LOGGER.warn("IrisExtendedShaderAccess: CapturedRenderingState lookup failed ({}), blockEntityId reset disabled", t.toString());
                  capturedRenderingStateInstance = null;
                  setCurrentBlockEntityMethod = null;
                  getCurrentBlockEntityMethod = null;
                  setCurrentBlockEntityMH = null;
                  getCurrentBlockEntityMH = null;
               }

               reflectionAvailable = true;
               MainRegistry.LOGGER
                  .info(
                     "IrisExtendedShaderAccess: reflection cached successfully (pipeline class: {}, blockEntityId reset: {})",
                     shaderRenderingPipelineClass.getName(),
                     setCurrentBlockEntityMethod != null
                  );
            } catch (Throwable t) {
               MainRegistry.LOGGER.warn("IrisExtendedShaderAccess: reflection unavailable ({}), falling back to vanilla", t.toString());
            }
         }
      }
   }

   private static Class<?> firstClass(String[] candidates) throws ClassNotFoundException {
      ClassNotFoundException last = null;

      for (String name : candidates) {
         try {
            return Class.forName(name);
         } catch (ClassNotFoundException e) {
            last = e;
         }
      }

      throw last != null ? last : new ClassNotFoundException(String.join(", ", candidates));
   }
}

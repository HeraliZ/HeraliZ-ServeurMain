package com.mrcrayfish.backpacked.client.renderer.backpack.value;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import com.mrcrayfish.backpacked.client.renderer.backpack.value.source.BaseSource;
import com.mrcrayfish.backpacked.client.renderer.backpack.value.source.StaticSource;
import java.util.function.Function;

public final class Value {
   public static final Value ZERO = new Value(new StaticSource(0.0), 0.0, 1.0);
   public static final Codec<Value> VALUE_CODEC = RecordCodecBuilder.create(
      builder -> builder.group(
            BaseSource.CODEC.fieldOf("source").forGetter(o -> o.source),
            Codec.DOUBLE.optionalFieldOf("base", 0.0).forGetter(o -> o.base),
            Codec.DOUBLE.optionalFieldOf("multiplier", 1.0).forGetter(o -> o.multiplier)
         )
         .apply(builder, Value::new)
   );
   public static final Codec<Value> CODEC = Codec.either(Codec.DOUBLE, VALUE_CODEC)
      .xmap(
         either -> (Value)either.map(val -> new Value(new StaticSource(val), 0.0, 1.0), Function.identity()),
         value -> value.source instanceof StaticSource source ? Either.left(source.value()) : Either.right(value)
      );
   private final BaseSource source;
   private final double multiplier;
   private final double base;

   public Value(BaseSource source, double base, double multiplier) {
      this.source = source;
      this.base = base;
      this.multiplier = multiplier;
   }

   public double getValue(BackpackRenderContext context) {
      return this.base + this.source.apply(context) * this.multiplier;
   }
}

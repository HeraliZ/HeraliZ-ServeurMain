package com.mrcrayfish.backpacked.common;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class BackpackedCodecs {
   public static final Codec<ImmutableList<EntityType<?>>> ENTITY_TYPE_LIST = Codec.either(
         BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf(), BuiltInRegistries.ENTITY_TYPE.byNameCodec()
      )
      .xmap(
         either -> (ImmutableList)either.map(ImmutableList::copyOf, ImmutableList::of),
         list -> list.size() == 1 ? Either.right((EntityType)list.get(0)) : Either.left(list)
      );
   public static final Codec<Set<String>> STRING_SET = Codec.either(Codec.STRING, Codec.STRING.listOf())
      .<List<String>>xmap(either -> either.map(List::of, Function.identity()), list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list))
      .xmap(list -> (Set<String>)new HashSet<String>(list), set -> List.copyOf(set));
   public static final Codec<HolderSet<Item>> ITEMS = BuiltInRegistries.ITEM
      .holderByNameCodec()
      .listOf()
      .xmap(HolderSet::direct, set -> set.stream().toList());
}

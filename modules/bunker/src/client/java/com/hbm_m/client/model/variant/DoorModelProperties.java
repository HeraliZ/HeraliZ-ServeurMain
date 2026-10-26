package com.hbm_m.client.model.variant;

import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import net.minecraftforge.client.model.data.ModelProperty;

public final class DoorModelProperties {
   public static final ModelProperty<DoorModelSelection> MODEL_SELECTION_PROPERTY = new ModelProperty();
   public static final ModelProperty<Boolean> DOOR_MOVING_PROPERTY = new ModelProperty();
   public static final ModelProperty<Boolean> OPEN_PROPERTY = new ModelProperty();
   public static final ModelProperty<Boolean> OVERLAP_PROPERTY = new ModelProperty();

   private DoorModelProperties() {
   }
}

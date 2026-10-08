package fr.heraliz.crac.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class TintedBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float r;
    private final float g;
    private final float b;
    private final float a;
    @Nullable
    private final RenderType forceType;

    public TintedBufferSource(MultiBufferSource delegate, float r, float g, float b, float a) {
        this(delegate, r, g, b, a, null);
    }

    public TintedBufferSource(MultiBufferSource delegate, float r, float g, float b, float a, @Nullable RenderType forceType) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.forceType = forceType;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        RenderType target = this.forceType != null ? this.forceType : type;
        return new TintedBufferSource.TintedVertexConsumer(this.delegate.getBuffer(target), this.r, this.g, this.b, this.a);
    }

    private static final class TintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float r;
        private final float g;
        private final float b;
        private final float a;

        private TintedVertexConsumer(VertexConsumer delegate, float r, float g, float b, float a) {
            this.delegate = delegate;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.delegate.color(clamp((int)(red * this.r)), clamp((int)(green * this.g)), clamp((int)(blue * this.b)), clamp((int)(alpha * this.a)));
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            this.delegate.color(red * this.r, green * this.g, blue * this.b, alpha * this.a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this.delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            this.delegate.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            this.delegate.unsetDefaultColor();
        }

        private static int clamp(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
        }
    }
}

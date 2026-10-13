package com.crest.client.core.mixin;

import com.crest.client.core.BlockLodModule;
import com.crest.client.core.FrameBudget;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;

/**
 * ponytail: Tiered Block LOD — hooks Sodium's own block mesher (which owns block
 * geometry whenever Sodium is present) and cheapens how DISTANT blocks are built
 * into section meshes, while leaving near blocks at full quality/resolution.
 *
 * Runs on Sodium's chunk-build worker threads. The camera position is read from
 * FrameBudget (volatile) — no main-thread calls per block. The tier is computed
 * once per block in renderModel and stored on the instance (one BlockRenderer is
 * reused across blocks within a single section build).
 */
@Mixin(BlockRenderer.class)
public abstract class SodiumBlockLodMixin {

    @Shadow protected BlockPos pos;
    @Shadow protected BlockAndTintGetter level;

    @Unique private int crestLodTier = 0;

    @Unique
    private int crestComputeTier(BlockPos p) {
        if (!BlockLodModule.active()) return 0;
        double dx = p.getX() + 0.5 - FrameBudget.cameraX();
        double dy = p.getY() + 0.5 - FrameBudget.cameraY();
        double dz = p.getZ() + 0.5 - FrameBudget.cameraZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        int t1 = BlockLodModule.tier1() * 16;
        int t2 = BlockLodModule.tier2() * 16;
        int t3 = BlockLodModule.tier3() * 16;
        if (distSq >= (double) t3 * t3) return 3;
        if (distSq >= (double) t2 * t2) return 2;
        if (distSq >= (double) t1 * t1) return 1;
        return 0;
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void crest$classify(net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
                                BlockState state, BlockPos p, BlockPos origin, CallbackInfo ci) {
        crestLodTier = crestComputeTier(p);
    }

    // Tier >= 1: skip per-vertex ambient occlusion (flat lighting) — the single
    // most expensive per-vertex op in the mesher. prepareAoInfo(true) enables AO;
    // cancelling forces the cheap flat path. Zero visible loss at distance.
    @Inject(method = "prepareAoInfo", at = @At("HEAD"), cancellable = true)
    private void crest$noAo(boolean useAmbientOcclusion, CallbackInfo ci) {
        if (crestLodTier >= 1) {
            ci.cancel();
        }
    }

    // Tier >= 2: aggressively cull faces adjacent to ANY non-air neighbor. Interior
    // faces of solid distant structures are never visible, so dropping them is
    // quality-preserving. shouldDrawSide returns true => the face is culled.
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void crest$aggressiveCull(Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (crestLodTier < 2) return;
        BlockPos here = this.pos;
        if (here == null) return;
        BlockPos n = here.relative(dir);
        BlockState nb = level.getBlockState(n);
        if (!nb.isAir()) {
            cir.setReturnValue(true); // cull this face
        }
    }

    // Tier >= 1: collapse cutout/translucent quads to the SOLID layer for distant
    // blocks. Solid quads are already SOLID (no-op); only transparent/leaves/glass
    // quads are promoted to opaque. Removes the extra sorted transparency passes
    // (big win) and is imperceptible at distance.
    @Inject(method = "processQuad", at = @At("HEAD"))
    private void crest$collapseLayer(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (crestLodTier >= 1) {
            quad.setRenderType(ChunkSectionLayer.SOLID);
        }
    }
}

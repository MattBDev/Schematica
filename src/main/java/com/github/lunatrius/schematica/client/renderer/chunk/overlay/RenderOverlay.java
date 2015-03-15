package com.github.lunatrius.schematica.client.renderer.chunk.overlay;

import com.github.lunatrius.core.client.renderer.GeometryMasks;
import com.github.lunatrius.core.client.renderer.GeometryTessellator;
import com.github.lunatrius.core.client.renderer.vertex.VertexFormats;
import com.github.lunatrius.schematica.client.renderer.chunk.CompiledOverlay;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class RenderOverlay extends RenderChunk {
    private final VertexBuffer vertexBuffer;

    public RenderOverlay(final World world, final RenderGlobal renderGlobal, final BlockPos pos, final int index) {
        super(world, renderGlobal, pos, index);
        this.vertexBuffer = OpenGlHelper.useVbo() ? new VertexBuffer(VertexFormats.ABSTRACT) : null;
    }

    @Override
    public VertexBuffer getVertexBufferByLayer(final int layer) {
        return this.vertexBuffer;
    }

    @Override
    public void rebuildChunk(final float x, final float y, final float z, final ChunkCompileTaskGenerator generator) {
        final CompiledOverlay compiledOverlay = new CompiledOverlay();
        final BlockPos from = getPosition();
        final BlockPos to = from.add(15, 15, 15);
        generator.getLock().lock();
        RegionRenderCache regionRenderCache;
        final SchematicWorld schematic = (SchematicWorld) this.world;

        try {
            if (generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) {
                return;
            }

            if (from.getX() < 0 || from.getZ() < 0 || from.getX() >= schematic.getWidth() || from.getZ() >= schematic.getLength()) {
                generator.setCompiledChunk(CompiledChunk.DUMMY);
                return;
            }

            regionRenderCache = new RegionRenderCache(this.world, from.add(-1, -1, -1), to.add(1, 1, 1), 1);
            generator.setCompiledChunk(compiledOverlay);
        } finally {
            generator.getLock().unlock();
        }

        final VisGraph visgraph = new VisGraph();

        if (!regionRenderCache.extendedLevelsInChunkCache()) {
            ++renderChunksUpdated;

            final World mcWorld = Minecraft.getMinecraft().theWorld;

            final EnumWorldBlockLayer layer = EnumWorldBlockLayer.TRANSLUCENT;
            final WorldRenderer worldRenderer = generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer);

            GeometryTessellator.setStaticDelta(ConfigurationHandler.blockDelta);

            for (BlockPos pos : (Iterable<BlockPos>) BlockPos.getAllInBox(from, to)) {
                if (schematic.isRenderingLayer && schematic.renderingLayer != pos.getY() || !schematic.isInside(pos)) {
                    continue;
                }

                boolean render = false;
                int sides = 0;
                int color = 0;

                final IBlockState schBlockState = schematic.getBlockState(pos);
                final Block schBlock = schBlockState.getBlock();

                if (schBlock.isOpaqueCube()) {
                    visgraph.func_178606_a(pos);
                }

                final BlockPos mcPos = pos.add(schematic.position);
                final IBlockState mcBlockState = mcWorld.getBlockState(mcPos);
                final Block mcBlock = mcBlockState.getBlock();

                final boolean isAirBlock = mcWorld.isAirBlock(mcPos) || ConfigurationHandler.isExtraAirBlock(mcBlock);

                if (!isAirBlock) {
                    if (schBlock == Blocks.air && ConfigurationHandler.highlightAir) {
                        render = true;
                        sides = GeometryMasks.Quad.ALL;
                        color = 0xBF00BF;
                    }
                }

                if (!render) {
                    if (ConfigurationHandler.highlight) {
                        if (!isAirBlock) {
                            if (schBlock != mcBlock) {
                                render = true;
                                color = 0xFF0000;
                            } else if (schBlock.getMetaFromState(schBlockState) != mcBlock.getMetaFromState(mcBlockState)) {
                                render = true;
                                color = 0xBF5F00;
                            }
                        } else if (!schematic.isAirBlock(pos)) {
                            render = true;
                            color = 0x00BFFF;
                        }
                    }

                    if (render) {
                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.DOWN), EnumFacing.DOWN)) {
                            sides |= GeometryMasks.Quad.DOWN;
                        }

                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.UP), EnumFacing.UP)) {
                            sides |= GeometryMasks.Quad.UP;
                        }

                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.NORTH), EnumFacing.NORTH)) {
                            sides |= GeometryMasks.Quad.NORTH;
                        }

                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.SOUTH), EnumFacing.SOUTH)) {
                            sides |= GeometryMasks.Quad.SOUTH;
                        }

                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.WEST), EnumFacing.WEST)) {
                            sides |= GeometryMasks.Quad.WEST;
                        }

                        if (schBlock.shouldSideBeRendered(schematic, pos.offset(EnumFacing.EAST), EnumFacing.EAST)) {
                            sides |= GeometryMasks.Quad.EAST;
                        }
                    }
                }

                if (render && sides != 0) {
                    if (!compiledOverlay.isLayerStarted(layer)) {
                        compiledOverlay.setLayerStarted(layer);
                        preRenderBlocks(worldRenderer, from);
                    }

                    GeometryTessellator.drawCuboid(worldRenderer, GL11.GL_QUADS, pos, sides, color, 0x3F);
                    compiledOverlay.setLayerUsed(layer);
                }
            }

            if (compiledOverlay.isLayerStarted(layer)) {
                postRenderBlocks(layer, x, y, z, worldRenderer, compiledOverlay);
            }
        }

        compiledOverlay.setVisibility(visgraph.computeVisibility());
    }

    @Override
    public void preRenderBlocks(final WorldRenderer worldRenderer, final BlockPos pos) {
        super.preRenderBlocks(worldRenderer, pos);

        worldRenderer.setVertexFormat(VertexFormats.ABSTRACT);
    }

    @Override
    public void deleteGlResources() {
        super.deleteGlResources();

        if (this.vertexBuffer != null) {
            this.vertexBuffer.deleteGlBuffers();
        }
    }
}

package com.aetherspellbooks.client.accessory;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** Wearable meshes and their UV islands in tools/accessory_art.py. */
final class AccessoryModels {
    private AccessoryModels() {}

    static ModelPart pendant(float clearance) {
        return bake(CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, 0.5f, -2, 8, 6, 4,
                        new CubeDeformation(0.45f, 0, clearance))
                .texOffs(24, 0).addBox(-0.5f, 4.5f, -2.8f - clearance, 1, 1, 1)
                .texOffs(24, 4).addBox(-1, 5.5f, -2.95f - clearance, 2, 2, 1), 32, 16);
    }

    static ModelPart glove(boolean right, boolean slim, boolean armored) {
        int width = slim ? 3 : 4;
        int u = slim ? 32 : 0;
        float x = right ? 1 - width : -1;
        // The mage sleeve uses the standard-width arm and a raised 6-pixel wrist band.
        float clearance = armored ? 1.35f : 0.35f;
        float y = armored ? 5.5f : 5;
        return bake(CubeListBuilder.create().mirror(!right)
                .texOffs(u, 0).addBox(x, y, -2, width, 5, 4,
                        new CubeDeformation(clearance, armored ? 0.6f : 0.05f, clearance))
                .texOffs(u, 10).addBox(x, 5, -2, width, 1, 4,
                        new CubeDeformation(clearance + 0.17f, 0.02f, clearance + 0.17f))
                .texOffs(u, 16).addBox(x + (width - 2) * 0.5f, y + 2, -2.68f - clearance, 2, 2, 1), 64, 24);
    }

    static ModelPart mantle() {
        return bake(CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3, 0, -1, 6, 3, 1)
                .texOffs(16, 0).addBox(-4, 3, -1, 8, 3, 1)
                .texOffs(0, 5).addBox(-5, 6, -1, 10, 8, 1)
                .texOffs(24, 6).addBox(-5, 14, -1, 1, 2, 1)
                .texOffs(32, 6).addBox(-3, 14, -1, 2, 2, 1)
                .texOffs(32, 6).addBox(1, 14, -1, 2, 2, 1)
                .texOffs(24, 6).addBox(4, 14, -1, 1, 2, 1)
                .texOffs(24, 11).addBox(-1, 1, -1.45f, 2, 2, 1), 40, 16);
    }

    private static ModelPart bake(CubeListBuilder cubes, int textureWidth, int textureHeight) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("accessory", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot().getChild("accessory");
    }
}

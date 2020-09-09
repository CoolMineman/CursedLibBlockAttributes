package concern;

import net.minecraft.util.maths.Vec3i;

public class BlockPos extends Vec3i {
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos offset(Direction direction) {
        switch (direction) {
            case UP:
                return new BlockPos(this.x, this.y + 1, this.z);
            case DOWN:
                return new BlockPos(this.x, this.y - 1, this.z);
            case NORTH:
                return new BlockPos(this.x, this.y, this.z - 1);
            case SOUTH:
                return new BlockPos(this.x, this.y, this.z + 1);
            case WEST:
                return new BlockPos(this.x - 1, this.y, this.z);
            case EAST:
                return new BlockPos(this.x + 1, this.y, this.z);
            default:
                throw new RuntimeException("Unreachable");
        }
    }
}

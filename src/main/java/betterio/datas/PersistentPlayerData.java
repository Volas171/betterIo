package betterio.datas;

import arc.struct.Array;
import mindustry.entities.type.BaseUnit;

import java.io.Serializable;

public class PersistentPlayerData implements Serializable {
    public String origName;
    public Array<BaseUnit> draugPets = new Array<>();
    public int bbIncrementor;
    public boolean spawnedPowerGen;
    public boolean spawnedLichPet;

    public PersistentPlayerData() {}
}

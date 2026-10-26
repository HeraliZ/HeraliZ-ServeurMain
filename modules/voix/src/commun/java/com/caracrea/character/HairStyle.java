package com.caracrea.character;

/**
 * Parameters of a procedural hairstyle. Lengths are in sub-pixels of the
 * 4x skin (a head face is 32 sub-pixels tall).
 *
 * @param fringe       height of the fringe on the forehead (0 = none)
 * @param fringeShape  outline of the fringe
 * @param sideLength   how far down the sides of the head the hair goes (back part of the side faces)
 * @param templeLength how far down the hair goes next to the face (sideburns / temples)
 * @param backLength   how far down the back of the head the hair goes
 * @param torsoLength  how far down the back of the torso the hair continues (0 = stops at the neck)
 * @param top          whether the top of the head is covered
 * @param volume       whether the hair is also drawn on the outer (hat) layer for thickness
 * @param texture      strand pattern
 * @param extra        special feature (ponytail, bun...)
 */
public record HairStyle(String id, Pool pool, int fringe, Fringe fringeShape, int sideLength, int templeLength,
                        int backLength, int torsoLength, boolean top, boolean volume, Texture texture, Extra extra) {

    public enum Fringe { NONE, STRAIGHT, SIDE_SWEPT, MIDDLE_PART, MESSY, CURTAIN, SPIKY }

    public enum Texture { STRAIGHT, WAVY, CURLY, DREADS, BUZZ }

    public enum Extra { NONE, PONYTAIL, BUN, BRAID, MOHAWK, UNDERCUT, SIDE_SHAVE, FRONT_STRANDS, AFRO }

    public boolean bald() {
        return !top && fringe == 0 && sideLength == 0 && backLength == 0 && extra == Extra.NONE;
    }

    public boolean longHair() {
        return torsoLength > 0;
    }
}

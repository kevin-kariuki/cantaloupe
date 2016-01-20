package edu.illinois.library.cantaloupe.resource.iiif.v1;

import edu.illinois.library.cantaloupe.image.Filter;
import org.junit.Test;

import static org.junit.Assert.*;

public class QualityTest {

    @Test
    public void testValues() {
        assertNotNull(Quality.valueOf("BITONAL"));
        assertNotNull(Quality.valueOf("COLOR"));
        assertNotNull(Quality.valueOf("GRAY"));
        assertNotNull(Quality.valueOf("NATIVE"));
        assertEquals(4, Quality.values().length);
    }

    @Test
    public void testToFilter() {
        assertEquals(Filter.BITONAL, Quality.BITONAL.toFilter());
        assertNull(Quality.COLOR.toFilter());
        assertEquals(Filter.GRAY, Quality.GRAY.toFilter());
        assertNull(Quality.NATIVE.toFilter());
    }

}

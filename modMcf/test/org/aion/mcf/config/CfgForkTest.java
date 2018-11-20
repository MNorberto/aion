/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.config;

import static junit.framework.TestCase.assertEquals;

import org.aion.zero.impl.config.CfgAion;
import org.junit.Test;

public class CfgForkTest {

    @Test
    public void getForkPropertyTest() {
        CfgAion cfg = CfgAion.inst();
        String forkProperty = cfg.getFork().getForkPropertyByNumber(1);
        assertEquals("2000000", forkProperty);
    }
}

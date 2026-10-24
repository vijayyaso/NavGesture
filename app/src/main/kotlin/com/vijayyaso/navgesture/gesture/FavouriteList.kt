// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.vijayyaso.navgesture.gesture

/** Ordered favourite packages, independent of Android storage and launch intents. */
object FavouriteList {
    fun decode(stored: String?): List<String> =
        if (stored.isNullOrEmpty()) emptyList() else stored.split(",")

    fun encode(packages: List<String>): String = packages.joinToString(",")

    fun usable(packages: List<String>, launchable: (String) -> Boolean): List<String> =
        packages.filter(launchable)

    /** Keeps the first item and invalid selections in place. */
    fun moveUp(packages: List<String>, index: Int): List<String> {
        if (index <= 0 || index >= packages.size) return packages
        return packages.toMutableList().apply {
            val previous = this[index - 1]
            this[index - 1] = this[index]
            this[index] = previous
        }
    }

    fun toggle(packages: List<String>, packageName: String): List<String> =
        if (packageName in packages) packages.filterNot { it == packageName }
        else packages + packageName
}

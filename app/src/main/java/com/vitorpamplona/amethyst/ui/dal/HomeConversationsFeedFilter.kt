/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class HomeConversationsFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultHomeFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultHomeFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultHomeFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.noteListCache))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val isGlobal = account.defaultHomeFollowList.value == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.liveHomeFollowLists.value?.users ?: emptySet()
        val followingTagSet = account.liveHomeFollowLists.value?.hashtags ?: emptySet()
        val followingGeohashSet = account.liveHomeFollowLists.value?.geotags ?: emptySet()

        val now = TimeUtils.now()

        return collection
            .asSequence()
            .filter {
                (
                    it.event is TextNoteEvent ||
                        it.event is PollNoteEvent ||
                        it.event is ChannelMessageEvent ||
                        it.event is LiveActivitiesChatMessageEvent ||
                        it.event is DraftEvent
                ) &&
                    (
                        isGlobal ||
                            it.author?.pubkeyHex in followingKeySet ||
                            it.event?.isTaggedHashes(followingTagSet) ?: false ||
                            it.event?.isTaggedGeoHashes(followingGeohashSet) ?: false
                    ) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if
                    // acceptable
                    (isHiddenList || it.author?.let { !account.isHidden(it) } ?: true) &&
                    ((it.event?.createdAt() ?: 0) < now) &&
                    !it.isNewThread()
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}

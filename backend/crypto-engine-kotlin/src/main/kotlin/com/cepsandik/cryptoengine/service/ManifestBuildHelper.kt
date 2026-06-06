package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.ContestInfo
import electionguard.ballot.Manifest
import electionguard.cli.ManifestBuilder

/**
 * Proto `ContestInfo` listesinden KMP `Manifest` inşa eder.
 *
 * Tek geopolitical unit + tek ballot style varsayımı.
 */
object ManifestBuildHelper {

    fun build(electionId: String, contests: List<ContestInfo>): Manifest {
        val builder = ManifestBuilder("election-$electionId")
        builder.addStyle("ballot-style-1", "district-1")

        contests.forEach { contestInfo ->
            val numberElected = if (contestInfo.numberElected > 0) contestInfo.numberElected else 1
            val voteVariation = if (numberElected == 1)
                Manifest.VoteVariationType.one_of_m
            else
                Manifest.VoteVariationType.n_of_m

            val cb = builder.addContest(contestInfo.contestId)
                .setVoteVariationType(voteVariation, numberElected, numberElected)
                .setGpunit("district-1")

            contestInfo.selectionIdsList.forEach { selId ->
                cb.addSelection(selId, selId)
            }
            cb.done()
        }

        return builder.build()
    }
}

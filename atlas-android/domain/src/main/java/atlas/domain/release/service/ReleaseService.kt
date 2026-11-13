package atlas.domain.release.service

import atlas.domain.release.interactor.GetApplicationRelease
import atlas.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(arguments: GetApplicationRelease.Arguments): Release?
}

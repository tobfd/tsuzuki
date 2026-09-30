package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.people.PeopleRepository
import com.tobfd.tsuzuki.core.model.CharacterAppearance
import com.tobfd.tsuzuki.core.model.CharacterDetail
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FavouriteKind
import com.tobfd.tsuzuki.core.model.ProductionRole
import com.tobfd.tsuzuki.core.model.StaffDetail
import com.tobfd.tsuzuki.core.model.VoicedCharacter

/** In-memory [PeopleRepository]; set the results the pages should get. */
class FakePeopleRepository : PeopleRepository {
    var characterResult: Result<CharacterDetail> = Result.failure(IllegalStateException("no character"))
    var staffResult: Result<StaffDetail> = Result.failure(IllegalStateException("no staff"))
    val appearancePages = mutableMapOf<Int, Result<ContentPage<CharacterAppearance>>>()
    val characterPages = mutableMapOf<Int, Result<ContentPage<VoicedCharacter>>>()
    val rolePages = mutableMapOf<Int, Result<ContentPage<ProductionRole>>>()
    var favouriteResult: Result<Unit> = Result.success(Unit)

    val favouriteToggles = mutableListOf<Pair<FavouriteKind, Int>>()
    val requestedPages = mutableListOf<Int>()

    override suspend fun character(id: Int) = characterResult

    override suspend fun characterAppearances(id: Int, page: Int): Result<ContentPage<CharacterAppearance>> {
        requestedPages += page
        return appearancePages[page] ?: Result.success(ContentPage(emptyList(), false))
    }

    override suspend fun staff(id: Int) = staffResult

    override suspend fun staffCharacters(id: Int, page: Int): Result<ContentPage<VoicedCharacter>> {
        requestedPages += page
        return characterPages[page] ?: Result.success(ContentPage(emptyList(), false))
    }

    override suspend fun staffRoles(id: Int, page: Int): Result<ContentPage<ProductionRole>> {
        requestedPages += page
        return rolePages[page] ?: Result.success(ContentPage(emptyList(), false))
    }

    override suspend fun toggleFavourite(kind: FavouriteKind, id: Int): Result<Unit> {
        favouriteToggles += kind to id
        return favouriteResult
    }
}

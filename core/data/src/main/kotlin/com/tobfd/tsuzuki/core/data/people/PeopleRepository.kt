package com.tobfd.tsuzuki.core.data.people

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.isFromCache
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.data.mapper.toPerson
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.adultContentAllowed
import com.tobfd.tsuzuki.core.model.CharacterAppearance
import com.tobfd.tsuzuki.core.model.CharacterDetail
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FavouriteKind
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.PersonName
import com.tobfd.tsuzuki.core.model.ProductionRole
import com.tobfd.tsuzuki.core.model.StaffDetail
import com.tobfd.tsuzuki.core.model.VoicedCharacter
import com.tobfd.tsuzuki.core.network.CharacterDetailQuery
import com.tobfd.tsuzuki.core.network.CharacterMediaQuery
import com.tobfd.tsuzuki.core.network.StaffCharacterPageQuery
import com.tobfd.tsuzuki.core.network.StaffDetailQuery
import com.tobfd.tsuzuki.core.network.StaffMediaPageQuery
import com.tobfd.tsuzuki.core.network.ToggleFavouriteMutation
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.fragment.CharacterAppearances
import com.tobfd.tsuzuki.core.network.fragment.StaffCharacters
import com.tobfd.tsuzuki.core.network.fragment.StaffRoles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** How long a people page counts as fresh: reopening it within this time makes no request. */
private val PAGE_MAX_AGE: Duration = Duration.ofHours(1)

/**
 * Character and staff pages (docs/ROADMAP.md, M8): one request per open, later pages of their lists
 * only on "Load more". Adult media are left out unless the viewer turned them on (these connections
 * have no `isAdult` argument).
 */
interface PeopleRepository {
    suspend fun character(id: Int): Result<CharacterDetail>

    /** Page [page] (2 and up) of the media a character appears in. */
    suspend fun characterAppearances(id: Int, page: Int): Result<ContentPage<CharacterAppearance>>

    suspend fun staff(id: Int): Result<StaffDetail>

    /** Page [page] (2 and up) of the characters a staff member voiced. */
    suspend fun staffCharacters(id: Int, page: Int): Result<ContentPage<VoicedCharacter>>

    /** Page [page] (2 and up) of a staff member's production roles. */
    suspend fun staffRoles(id: Int, page: Int): Result<ContentPage<ProductionRole>>

    /** Toggles the heart on a character or staff page. */
    suspend fun toggleFavourite(kind: FavouriteKind, id: Int): Result<Unit>
}

@Singleton
internal class DefaultPeopleRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : PeopleRepository {

    private val fetchedAt = ConcurrentHashMap<Pair<FavouriteKind, Int>, Instant>()

    override suspend fun character(id: Int): Result<CharacterDetail> {
        val adult = sessionRepository.adultContentAllowed()
        return page(FavouriteKind.Character, id, CharacterDetailQuery(id = id)) { data ->
            data.Character?.let { character ->
                CharacterDetail(
                    id = character.id,
                    name = PersonName(
                        userPreferred = character.name?.userPreferred ?: character.name?.full.orEmpty(),
                        full = character.name?.full,
                        native = character.name?.native,
                        alternative = character.name?.alternative.orEmpty().filterNotNull().filter { it.isNotBlank() }
                    ),
                    imageUrl = character.image?.large,
                    descriptionHtml = character.description?.takeIf { it.isNotBlank() },
                    gender = character.gender,
                    age = character.age,
                    bloodType = character.bloodType,
                    dateOfBirth = character.dateOfBirth?.let { FuzzyDate.orNull(it.year, it.month, it.day) },
                    favourites = character.favourites,
                    isFavourite = character.isFavourite,
                    siteUrl = character.siteUrl,
                    appearances = character.media?.characterAppearances.toPage(adult)
                )
            }
        }
    }

    override suspend fun characterAppearances(id: Int, page: Int): Result<ContentPage<CharacterAppearance>> {
        val adult = sessionRepository.adultContentAllowed()
        return morePages(CharacterMediaQuery(id = id, page = page)) { data ->
            data.Character?.media?.characterAppearances.toPage(adult)
        }
    }

    override suspend fun staff(id: Int): Result<StaffDetail> {
        val adult = sessionRepository.adultContentAllowed()
        return page(FavouriteKind.Staff, id, StaffDetailQuery(id = id)) { data ->
            data.Staff?.let { staff ->
                StaffDetail(
                    id = staff.id,
                    name = PersonName(
                        userPreferred = staff.name?.userPreferred ?: staff.name?.full.orEmpty(),
                        full = staff.name?.full,
                        native = staff.name?.native,
                        alternative = staff.name?.alternative.orEmpty().filterNotNull().filter { it.isNotBlank() }
                    ),
                    imageUrl = staff.image?.large,
                    descriptionHtml = staff.description?.takeIf { it.isNotBlank() },
                    occupations = staff.primaryOccupations.orEmpty().filterNotNull(),
                    gender = staff.gender,
                    age = staff.age,
                    dateOfBirth = staff.dateOfBirth?.let { FuzzyDate.orNull(it.year, it.month, it.day) },
                    homeTown = staff.homeTown?.takeIf { it.isNotBlank() },
                    yearsActive = staff.yearsActive.orEmpty().filterNotNull(),
                    favourites = staff.favourites,
                    isFavourite = staff.isFavourite,
                    siteUrl = staff.siteUrl,
                    characters = staff.characters?.staffCharacters.toPage(adult),
                    roles = staff.staffMedia?.staffRoles.toPage(adult)
                )
            }
        }
    }

    override suspend fun staffCharacters(id: Int, page: Int): Result<ContentPage<VoicedCharacter>> {
        val adult = sessionRepository.adultContentAllowed()
        return morePages(StaffCharacterPageQuery(id = id, page = page)) { data ->
            data.Staff?.characters?.staffCharacters.toPage(adult)
        }
    }

    override suspend fun staffRoles(id: Int, page: Int): Result<ContentPage<ProductionRole>> {
        val adult = sessionRepository.adultContentAllowed()
        return morePages(StaffMediaPageQuery(id = id, page = page)) { data ->
            data.Staff?.staffMedia?.staffRoles.toPage(adult)
        }
    }

    override suspend fun toggleFavourite(kind: FavouriteKind, id: Int): Result<Unit> {
        val mutation = when (kind) {
            FavouriteKind.Character -> ToggleFavouriteMutation(characterId = Optional.present(id))
            FavouriteKind.Staff -> ToggleFavouriteMutation(staffId = Optional.present(id))
        }
        val response = try {
            apolloClient.mutation(mutation).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        // The cached page still has the old heart; the next open asks AniList again.
        fetchedAt.remove(kind to id)
        return Result.success(Unit)
    }

    /** A whole page: from the cache while fresh, else from AniList (the cache stays the offline fallback). */
    private suspend fun <D : Query.Data, T : Any> page(
        kind: FavouriteKind,
        id: Int,
        query: Query<D>,
        map: (D) -> T?
    ): Result<T> {
        val last = fetchedAt[kind to id]
        val fresh = last != null && Duration.between(last, clock.instant()) < PAGE_MAX_AGE
        val response = execute(query, if (fresh) FetchPolicy.CacheFirst else FetchPolicy.NetworkFirst)
            .getOrElse { return Result.failure(it) }
        val model = response.data?.let(map) ?: return Result.failure(AppError.NotFound)
        if (!response.isFromCache) fetchedAt[kind to id] = clock.instant()
        return Result.success(model)
    }

    private suspend fun <D : Query.Data, T : Any> morePages(query: Query<D>, map: (D) -> T?): Result<T> {
        val response = execute(query, FetchPolicy.NetworkFirst).getOrElse { return Result.failure(it) }
        return response.data?.let(map)?.let { Result.success(it) } ?: Result.failure(AppError.NotFound)
    }

    private suspend fun <D : Query.Data> execute(query: Query<D>, policy: FetchPolicy): Result<ApolloResponse<D>> {
        val response = try {
            apolloClient.query(query).fetchPolicy(policy).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        if (response.data == null) return Result.failure(response.appErrorOrNull() ?: AppError.NotFound)
        return Result.success(response)
    }
}

internal fun CharacterAppearances?.toPage(adult: Boolean): ContentPage<CharacterAppearance> = ContentPage(
    items = this?.edges.orEmpty().mapNotNull { edge ->
        val media = edge?.node?.mediaCard?.toModel()?.takeIf { adult || !it.isAdult } ?: return@mapNotNull null
        CharacterAppearance(media, edge.characterRole?.rawValue, edge.voiceActors?.firstOrNull()?.staffLite?.toPerson())
    }.distinctBy { it.media.id },
    hasNextPage = this?.pageInfo?.hasNextPage == true
)

internal fun StaffCharacters?.toPage(adult: Boolean): ContentPage<VoicedCharacter> = ContentPage(
    items = this?.edges.orEmpty().mapNotNull { edge ->
        val character = edge?.node?.characterLite?.toPerson() ?: return@mapNotNull null
        val media = edge.media.orEmpty().firstNotNullOfOrNull { it?.mediaCard?.toModel() }
        if (media?.isAdult == true && !adult) return@mapNotNull null
        VoicedCharacter(character, edge.role?.rawValue, media)
    }.distinctBy { it.character.id },
    hasNextPage = this?.pageInfo?.hasNextPage == true
)

internal fun StaffRoles?.toPage(adult: Boolean): ContentPage<ProductionRole> = ContentPage(
    items = this?.edges.orEmpty().mapNotNull { edge ->
        val media = edge?.node?.mediaCard?.toModel()?.takeIf { adult || !it.isAdult } ?: return@mapNotNull null
        ProductionRole(media, edge.staffRole)
    }.distinctBy { it.media.id to it.role },
    hasNextPage = this?.pageInfo?.hasNextPage == true
)

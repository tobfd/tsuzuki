package com.tobfd.tsuzuki.core.testing

import androidx.paging.PagingData
import com.tobfd.tsuzuki.core.data.browse.BrowseRepository
import com.tobfd.tsuzuki.core.model.BrowseHome
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.FilterOptions
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory [BrowseRepository] that records what was asked for. */
class FakeBrowseRepository : BrowseRepository {
    var homeResult: Result<BrowseHome> = Result.success(BrowseHome(emptyList(), emptyList()))
    var optionsResult: Result<FilterOptions> = Result.success(FilterOptions(emptyList(), emptyList()))
    var results: List<SearchResult> = emptyList()

    val homeRequests = mutableListOf<MediaType>()
    val searches = mutableListOf<BrowseQuery>()
    var optionRequests = 0
        private set

    override suspend fun home(type: MediaType): Result<BrowseHome> {
        homeRequests += type
        return homeResult
    }

    override fun search(query: BrowseQuery): Flow<PagingData<SearchResult>> {
        searches += query
        return flowOf(PagingData.from(results))
    }

    override suspend fun filterOptions(): Result<FilterOptions> {
        optionRequests++
        return optionsResult
    }
}

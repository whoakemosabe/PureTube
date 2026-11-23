package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext

/**
 * An OAuth sign-in reads YouTube through the TV client. Some public features only exist in
 * WEB responses: TV search results carry no filter panel, and a TV channel browse ignores
 * the search query and answers with channel tiles. Those reads use the public WEB client;
 * a cookie (WEB) sign-in already reads WEB and keeps its identity.
 */
internal fun RequestContext.forPublicWebRead(): RequestContext =
    if (accessToken != null && webSession?.signedIn != true) anonymous() else this

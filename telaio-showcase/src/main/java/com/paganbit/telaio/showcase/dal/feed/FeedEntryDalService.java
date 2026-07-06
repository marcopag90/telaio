package com.paganbit.telaio.showcase.dal.feed;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.metrics.annotation.DalMetrics;

/**
 * <h2>Use case — partial (append-only) exposure</h2>
 * <p>
 * Exposes the {@link FeedEntry} entity as an <em>append-only</em> REST resource: clients may post new
 * entries ({@code POST /dal/v1/feed}) and list them ({@code GET /dal/v1/feed?q=&page=&size=}), but cannot
 * read a single entry by id, update, or delete one.
 * <p>
 * This is achieved declaratively with {@code @DalService(operations = { CREATE, READ })}. Unlike the
 * override-and-throw workaround, the omitted operations are <b>structurally absent</b>:
 * <ul>
 *   <li>they do not appear in the generated OpenAPI document (Swagger UI shows only POST and GET on the
 *       collection — no item path at all);</li>
 *   <li>a request to them never reaches the {@link com.paganbit.telaio.core.Dal}: {@code GET /dal/v1/feed/{id}}
 *       returns {@code 404} (the item URI exposes no operation), while a hypothetical write on the
 *       collection would return {@code 405} with an {@code Allow} header.</li>
 * </ul>
 * The {@link JpaDal} bean itself keeps every CRUD method, so trusted in-process code (and DAL-to-DAL
 * composition) is unaffected — the restriction is a remote-boundary concern only.
 */
@DalService(name = "feed", operations = {DalOperationType.CREATE, DalOperationType.READ})
@DalMetrics(enabled = false)
public class FeedEntryDalService extends JpaDal<FeedEntry, Long> {
}

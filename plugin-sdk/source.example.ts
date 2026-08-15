export default defineSource({
  async search(ctx, query, page) {
    const response = await ctx.http.get(`/search?q=${ctx.url.encode(query)}&page=${page}`)
    return ctx.html(response).select(".comic-card").map(card => ({
      title: card.selectText(".title"),
      url: card.selectAttr("a", "href"),
      cover: card.selectAttr("img", "src")
    }))
  },

  async detail(ctx, url) {
    const document = await ctx.html(await ctx.http.get(url))
    return {
      title: document.selectText("h1"),
      author: document.selectText(".author"),
      description: document.selectText(".description"),
      chapters: document.select(".chapter a").map(link => ({
        title: link.text(),
        url: link.attr("href")
      }))
    }
  },

  async pages(ctx, chapterUrl) {
    const document = await ctx.html(await ctx.http.get(chapterUrl))
    return document.select(".page img").map(image => ({
      url: image.attr("data-src") ?? image.attr("src")
    }))
  }
})

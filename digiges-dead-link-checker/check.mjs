import * as link from 'linkinator'

async function run() {
  const checker = new link.LinkChecker()
  const reportedUrl = new Set()

  console.log('"status","url","parent"')
  checker.on('link', result => {
    if (result.state !== 'SKIPPED' && !reportedUrl.has(result.url)) {
      reportedUrl.add(result.url)
      if (result.status !== 200) {
        console.log(`"${result.status}","${result.url}","${result.parent}"`)
      }
    }
  })

  await checker.check({
    path: process.env['WEB_ROOT'] || 'https://www.digitale-gesellschaft.ch',
    recurse: true,
    concurrency: 5,
    retryErrors: true,
    retryErrorsCount: 2,
    timeout: 2000,
    linksToSkip: [
      '/feed/$'
    ]
  })
}

await run()

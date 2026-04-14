import { headers } from "next/headers"

type TeamsPageProps = {
  params: Promise<{ path?: string[] }>
}

async function teamWebOrigin(): Promise<string> {
  const configured =
    process.env.TEAM_WEB_PUBLIC_ORIGIN ??
    process.env.NEXT_PUBLIC_TEAM_WEB_ORIGIN ??
    ""
  if (configured.trim() !== "") {
    return configured.replace(/\/+$/, "")
  }

  const h = await headers()
  const host = h.get("x-forwarded-host") ?? h.get("host") ?? "localhost:3000"
  const proto = (h.get("x-forwarded-proto") ?? "http").split(",")[0]?.trim() || "http"
  const hostname = host.split(":")[0]
  return `${proto}://${hostname}:3002`
}

export default async function TeamsPage({ params }: TeamsPageProps) {
  const { path } = await params
  const suffix = path && path.length > 0 ? `/${path.map(encodeURIComponent).join("/")}` : ""
  const src = `${await teamWebOrigin()}/teams${suffix}`
  return (
    <section className="w-full">
      <div className="w-full overflow-hidden bg-white">
        <iframe
          src={src}
          title="Team Management"
          className="h-[calc(100vh-4rem)] w-full border-0"
          loading="lazy"
        />
      </div>
    </section>
  )
}

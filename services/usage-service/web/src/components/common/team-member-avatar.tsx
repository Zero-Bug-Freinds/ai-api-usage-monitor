"use client"

import { generateAvatarUrl, type AvatarConfig } from "@/lib/usage/avatar-url"

type TeamMemberAvatarProps = {
  userId: string
  size?: number
  className?: string
  avatarConfig?: AvatarConfig
}

export function TeamMemberAvatar({ userId, size = 24, className = "", avatarConfig }: TeamMemberAvatarProps) {
  const src = generateAvatarUrl(userId, avatarConfig)
  const dim = `${size}px`
  return (
    <img
      src={src}
      alt=""
      width={size}
      height={size}
      loading="lazy"
      decoding="async"
      className={`shrink-0 rounded-full object-cover ring-1 ring-border ${className}`.trim()}
      style={{ width: dim, height: dim }}
    />
  )
}

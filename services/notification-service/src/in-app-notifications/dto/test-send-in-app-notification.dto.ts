import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class TestSendInAppNotificationDto {
  @IsString()
  @MinLength(1)
  @MaxLength(320)
  targetUserId!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(200)
  title!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(10_000)
  body!: string;

  @IsOptional()
  @IsString()
  @MaxLength(100)
  type?: string;
}


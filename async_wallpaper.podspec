Pod::Spec.new do |s|
  s.name             = 'async_wallpaper'
  s.version          = '3.1.0'
  s.summary          = 'Flutter wallpaper plugin with Android apply and cross-platform download support.'
  s.description      = <<-DESC
A Flutter plugin for wallpaper operations. Android supports apply/live wallpaper flows,
while iOS supports wallpaper download to Photos.
                       DESC
  s.homepage         = 'https://github.com/codenameakshay/async_wallpaper'
  s.license          = { :file => 'LICENSE' }
  s.author           = { 'codenameakshay' => 'contact@hashstudios.dev' }
  s.source           = { :path => '.' }
  s.source_files     = 'ios/Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '13.0'
  s.swift_version = '5.0'
end

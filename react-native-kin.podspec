require 'json'
pkg = JSON.parse(File.read("package.json"))

Pod::Spec.new do |s|
  s.name         = pkg["name"]
  s.version      = pkg["version"]
  s.summary      = pkg["description"]
  s.requires_arc = true
  s.license      = pkg["license"]
  s.homepage     = pkg["homepage"]
  s.author       = pkg["author"]
  s.source       = { :git => pkg["repository"]["url"] }
  s.source_files = 'ios/*.{h,m,swift}'
  s.platform     = :ios, "8.0"
  s.static_framework = true
  s.dependency 'React'
  s.dependency 'KinEcosystem', '0.5.1'
  s.dependency 'JWT', '3.0.0-beta.8', :modular_headers => true
end
